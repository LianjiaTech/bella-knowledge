## 目标

为 `DELETE /v1/files/{file_id}` 增加可选的 `force` 查询参数，并把目录删除变成一个有明确冲突语义、完整子树语义和事务一致性保证的操作：

- `force=false`（包括未传参）时，非空目录不产生任何状态或关系变更，并返回稳定的 HTTP 409 冲突错误；普通文件和空目录继续沿用现有删除行为。
- `force=true` 时，删除目标目录及其整棵受支持子树，而不是只删除目录入口；每个节点继续使用其现有的状态、`file_entry` 和 `FILE_DELETED` 语义。
- 删除、上传、创建目录和移动之间以同一套 `file_entry` 父入口锁规则协调，保证提交后不存在存活子节点指向已删除父入口；任一步骤失败时整体回滚。
- 不修复本次变更前已经存在的孤儿节点；历史数据修复另行处理。

## 当前上下文

- `FileController` 当前只接收 `file_id`，并直接调用 `FileService.delete`。
- `FileService.delete` 在事务内将单个 `file` 标记为 `DELETED`，由 `FileRepo.updateFile` 删除单个 `file_entry`；当前没有子树快照或子树事件编排。
- `file_entry.parent_entry_id` 是入口模式下的父子权威关系；`FileEntryRepo` 已有按层 BFS、`FOR UPDATE`、SQL 分块和 `MAX_TREE_DEPTH=64` 的非空目录迁移实现，可复用其锁序和深度校验思路。
- 本 issue 的前置条件是仓库已完成切换到 `file_entry` 读写模式；闭包表停写/清理由独立 issue 负责，本方案不再为 closure/dual 兼容增加实现分支。
- 文件物理表按资源类型/文件 ID 分片，`file_entry` 按空间分片；现有跨空间迁移测试证明了在当前数据访问层的事务与锁测试基础，但这不是跨独立数据库的分布式事务承诺。

## 方案

### 1. API 与稳定错误

- 将 `FileController.delete` 改为接收 `@RequestParam(value = "force", required = false, defaultValue = "false") boolean force`，并调用新的 `fileService.delete(fileDB, force)`；保留现有返回体和普通文件删除兼容性。
- 新增专用的目录冲突异常（例如 `DirectoryNotEmptyException`），携带稳定错误码 `file_directory_not_empty` 和固定英文消息 `Directory is not empty; set force=true to delete it.`。异常类型不复用泛化的 `IllegalArgumentException`，避免错误码和 HTTP 语义漂移。
- 在 `FileApiResponseAdvice` 的统一异常映射中将该异常映射为 HTTP 409，错误类型沿用项目的 `invalid_request_error`，并把异常错误码写入现有 `OpenAiErrorDetails.code` 字段；其他异常映射保持不变。
- 子树规模超限使用同一冲突类的独立错误码 `file_subtree_too_large`，HTTP 409，消息明确说明限制和 `force` 操作未执行。超限必须在任何写操作前返回。

### 2. 权威快照、锁序与并发边界

#### 权威关系选择

- 在已切换的 `file-entry.write-mode=entry` 模式下，以 `file_entry` 为删除快照的唯一权威关系：先锁定目标 active entry，再逐层查询其直接子节点并对每层结果 `FOR UPDATE`；只沿 `DIRECTORY`/入口类型继续展开，文件和其他受支持资源作为叶节点纳入快照。
- 不读取、不写入、不校验 `file_closure`。闭包表残留清理、closure 路由及 dual 到 entry 的历史切换由独立 issue 负责；本 issue 的正确性边界只针对已经切换完成的 `file_entry` 模式。

#### 锁序

- 先按稳定全序锁定目标根 `file_entry`，再按 BFS 层序锁定子入口；每层按 `entry_id` 排序并按 `sqlInChunkSize` 分块查询。
- 与创建目录/上传的协调点是父入口：创建子节点必须在插入前锁定父入口；删除先持有根及已发现层的锁，再进行下一层扫描。与移动的协调点是源根、目标父入口和被移动子树，沿用现有迁移的锚点全序规则，避免 A→B/B→A 反向等待。
- 删除快照完成后再次检查根仍 active、所有快照节点仍 active 且每个子节点的父关系仍属于快照；出现并发新增、移动或状态变化时让数据库锁等待后重新验证并失败回滚，不接受“先删父、后补子”的弱一致流程。
- 只处理同一当前关系空间内由目标目录可达的节点；文件状态按快照节点分组到物理文件分片，`file_entry` 按当前关系空间路由；两者不得共用“文件 ID 所在分片”这一推导。发现跨空间父子关系、未知节点类型或超过 `MAX_TREE_DEPTH=64` 时在写入前失败，不能把异常数据当成合法子树删除。

#### 规模限制

- 保留现有 `MAX_TREE_DEPTH=64` 作为硬深度上限；新增 `bella.file-api.file-entry.delete-subtree-max-nodes` 配置，默认 `10000`，用于限制一次 `force=true` 的节点数，防止无限持锁和超大 SQL。
- 节点数达到上限时停止继续扫描并返回 `file_subtree_too_large`；根节点、所有资源状态和 `file_entry` 均不写入。该限制不改变“任意层级”的语义，合法树只要深度不超过 64 且节点数在配置上限内即可递归删除。

### 3. 删除编排与资源类型语义

- 在 `FileService` 增加“构造并锁定快照 → 预检 → 批量状态变更/关系清理 → 广播”的子树删除编排，并保留单节点删除作为同一底层删除原语的单节点调用；不要逐节点调用现有 public `delete`，避免重复查询、嵌套事务和父节点先广播造成部分完成观感。
- 快照按 `file_id` 携带完整 `FileDB`、`FileType`、`NodeType` 和原始 `OpenAIFile` 事件数据。`FILE`、`DIRECTORY`、`RESOURCE` 均纳入支持范围；每类节点沿用当前 `FileType.needsDirectorySupport()` 判断是否清理目录关系，未知类型在预检阶段拒绝。
- `FileRepo` 提供按物理文件分片分组的批量 `DELETED` 更新；每个更新必须校验影响行数与预期数量。`FileEntryRepo` 按当前关系空间和快照 entry ID 分块删除；本 issue 不调用 `deleteFileClosure`，也不为 closure/dual 模式保留兼容分支。
- 批量关系清理与文件状态更新放在现有 service/repository `@Transactional(rollbackFor = Exception.class)` 边界内；任何分片更新计数不符、关系清理失败、事件准备失败或深度/关系校验失败都抛异常，回滚全部数据库写入。
- 所有节点数据库写入成功后，按稳定快照顺序复用现有 `FILE_DELETED` 广播 payload 和操作者上下文逐节点广播，保持下游按节点消费的兼容性；默认拒绝、预检失败和事务失败不发送删除事件。若当前广播实现可能在事务回滚前异步执行，则把发送动作接到现有事务提交后的回调/可靠投递机制，禁止失败事务泄露成功事件。

### 4. 跨分片事务不变量

- 先确认当前 `DSLContextHolder` 路由下不同物理表仍由同一个 Spring 事务资源管理器参与；在该边界内，文件分片和入口分片的更新必须全部在一个本地数据库事务中完成。
- 若部署形态把这些分片拆成独立数据库连接且当前事务管理器不能提供原子提交，本实现不伪造“整体回滚”保证：预检只允许能被同一事务覆盖的节点集合，否则返回明确的内部不支持错误并不写入；补偿删除不是本 issue 的默认一致性方案。
- 通过故障注入测试在状态更新、entry 删除和广播准备各阶段抛错，验证父、子和 `file_entry` 均保持删除前状态，且重试仍可得到同一结果。广播使用提交后触发时，失败事务不产生 `FILE_DELETED`。

### 5. 实施边界与文件范围

- API/错误：`FileController`、`FileException`、`FileApiResponseAdvice`。
- 服务编排：`FileService`，必要时抽出仅供服务调用的删除快照/批量删除协作对象，不改上传、mkdir、move 的公开请求语义。
- 数据访问：`FileRepo`、`FileEntryRepo`，复用现有分块、深度限制和锁序 helper；同步补齐创建目录/移动内部锁点，使它们与删除共用父入口锁协议。不得扩展 closure 表访问。
- 配置：`application.yml` 及测试/本地配置，为子树节点上限提供可覆盖值；不新增历史孤儿修复任务。
- 测试：优先扩展现有 `FileServiceBroadcastTest`、`FileServiceResourceTest`、`FileEntryRepoTest` 和跨空间锁/事务测试的模式，新增删除专用 service/repository 集成测试。

## 非目标

- 不修改上传、创建目录、移动接口的公开参数、返回体或业务语义；仅补充其内部父入口锁协作以满足删除并发不变量。
- 不自动扫描、修复或重挂本次变更前已存在的孤儿节点。
- 不把对象存储物理删除或 closure 表清理纳入本事务；closure 表迁移/清理由独立 issue 负责。
- 不为跨独立数据库引入新的分布式事务框架或依赖补偿队列；若当前运行环境无法满足本地事务边界，应先以可验证的预检/拒绝保护数据，而不是静默部分删除。

## 验证

### API 与普通路径

- Controller/API 测试覆盖 `force` 未传、`false`、`true` 的绑定；普通文件和空目录在三种关系写模式下保持原有成功响应。
- 非空目录默认删除返回 HTTP 409、错误码 `file_directory_not_empty` 和稳定提示；断言 `file` 状态、`file_entry` 和广播均无变化；closure 表由独立 issue 负责。
- 超过节点上限或超过深度上限返回明确错误；断言任何关系和状态都未写入。

### 子树与越界

- 构造单层目录、多层目录、文件与 `RESOURCE` 混合节点，断言 `force=true` 删除根及所有后代，根外同空间节点、其他目录和其他空间节点保持 active 且关系不变。
- 在已切换的 `file_entry` 模式验证：权威快照选取正确，`file_entry` 全部清理，删除后不存在存活 entry 指向已删除入口；断言本 issue 不访问 closure 表。额外构造一个先跨空间移动、再删除其包含叶节点的子树，验证 `file` 状态仍按原物理文件分片更新，而 `file_entry` 按移动后的关系空间读取和清理。
- 逐节点核对现有 `FILE_DELETED` 事件数量、顺序、payload 和操作者信息；删除失败、冲突和回滚场景断言零事件或仅有提交后事件。

### 事务与并发

- 对状态更新、entry 删除和广播准备逐点注入失败，验证“全成功或全回滚”，不存在父已删子存活的中间提交。
- 使用现有 `CountDownLatch`/`TransactionTemplate` 测试风格并发执行删除与上传、创建目录、同空间移动、跨空间移动：验证竞争方要么在删除提交前完成并被同一快照处理，要么在删除提交后失败/重新读取，不产生悬空父关系。
- 增加反向锁序场景验证无死锁环；增加跨文件分片/入口分片覆盖验证事务管理器实际覆盖范围，若测试环境不能证明跨独立连接原子性，则将该边界作为发布前置条件而非标记为已满足。

### 验收不变量

- 任一成功删除后：目标子树内无 active 节点，无 active `file_entry`；closure 表不属于本 issue 验收范围；目标子树外无状态或关系变化。
- 任一拒绝或失败后：所有目标节点和父子关系与操作前一致，且不会出现删除事件泄露。
- 任一并发提交后：所有 active 子节点的父入口也 active 且可解析；祖先路径查询不因本次删除产生 HTTP 500。
