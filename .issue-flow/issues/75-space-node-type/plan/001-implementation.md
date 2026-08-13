## 目标

- 在现有 `/v1/files` API 下新增一个按空间返回节点类型数量的只读接口，避免调用方分页读取全部节点后自行计数。
- 统计目标 space 下当前有效的 `file`、`directory`、`resource` 三类节点，并以固定字段返回三类数量；没有对应节点时返回 `0`。
- 保持现有文件 API 的认证、响应处理、space 隔离和分片路由约定，不改变节点创建、删除、移动或分页查询语义。

## 当前上下文

- `api/src/main/java/com/ke/bella/files/api/FileController.java` 使用类级别 `/v1/files` 路径和 `@FileAPI`，`list` 接口从 `BellaContextHelper.getOperateSpaceCode()` 获取当前操作空间；`pageFiles` 已支持请求体中的 `space_code`。
- `FileService` 将文件查询委托给 `FileRepo`，`FileRepo` 通过 `space_code` 的 hash 将同一空间路由到一个 `file_<0-15>` 分片；因此单个 space 的统计不需要跨分片汇总。
- `FileRepo.listFile` 的有效范围是 `FILE.STATUS = FileStatus.NOT_DELETED`，并结合 `file_closure` 只查询根层级且排除 `resource`。统计接口不能复用该分页查询，因为本需求需要三类节点、包含 resource，并且不需要闭包表层级过滤。
- `NodeType` 定义 `file`、`directory`、`resource`；目录历史数据通过 `is_dir = 1` 兼容识别，新增和现有页面查询同时使用 `node_type`/`is_dir` 语义。数据库 `file` 表的 `status` 注释明确 `0` 为未删除、`-1` 为已删除。
- 当前 `file` 表已有 `space_code`、`status`、`node_type` 字段，但初始化/迁移索引主要服务文件名和分页场景，未发现覆盖统计过滤与分组的 `(space_code, status, node_type)` 索引。

## 方案

### 1. 确定接口契约和空间来源

- 新增 `GET /v1/files/count`，沿用文件 API 的认证和 `FileApiResponseAdvice` 响应处理。
- 接受可选查询参数 `space_code`：有值时统计该 space；省略时回退到 `BellaContextHelper.getOperateSpaceCode()`，与当前文件列表接口保持一致。对最终解析出的 space 做非空校验，避免无条件查询空空间。
- 返回专用 DTO（建议放在 `api/src/main/java/com/ke/bella/files/protocol`）并使用明确的 snake_case 字段：`file_count`、`directory_count`、`resource_count`。响应直接是该 DTO，由现有 API 响应链路序列化，不引入分页包装或动态 map；三个字段始终输出，空 space 为 `0`。
- 接口范围仅为整个 space，不接受 `ancestor_id`、分页、purpose、容量或明细参数；这与 issue 的非目标一致。

### 2. 增加服务与仓储聚合路径

- 在 `FileService` 增加按 space 获取统计结果的方法，控制器只负责解析参数和调用服务，不复制数据库条件。
- 在 `FileRepo` 增加统计专用查询：按最终 space 计算同一分片键，在对应 `file_<shard>` 上以 `status = FileStatus.NOT_DELETED` 和 `space_code = ?` 过滤，再按 `node_type` `GROUP BY` 聚合。
- 将聚合结果映射到固定 DTO/结果对象，并按 `NodeType.values()` 初始化三类为 `0`；数据库返回未知或空 `node_type` 时不把它静默计入已知类型，记录异常或按现有数据完整性策略失败，避免统计口径悄然错误。
- 对历史目录兼容数据，统计分类条件采用与 `NodeType.from(FileDB)` 一致的优先级：`is_dir = 1` 归入 `directory`，否则使用合法 `node_type`；若直接依赖 `node_type` 聚合无法覆盖历史 `is_dir` 数据，则在 SQL 中将 `is_dir = 1` 映射为 `directory`，非目录按 `node_type` 映射。实现前以迁移数据和生成表定义确认字段默认值，避免把目录重复计数。
- 不通过 `file_closure` 统计：闭包表只表达层级关系，直接按 file 表统计可覆盖 resource，并避免根节点/子树语义把节点漏掉；删除操作将 `status` 置为 `DELETED`，因此自动排除已删除节点。

### 3. 补充统计索引并控制迁移范围

- 新增数据库迁移，为所有现有 `file_0` 至 `file_15` 增加 `(space_code, status, node_type)` 复合索引；索引命名遵循现有 `idx_*` 约定，并确认重复执行/升级顺序符合项目迁移机制。
- 若执行计划显示 MySQL 已能通过现有索引覆盖过滤，则保留迁移作为可审阅的性能决策记录；否则将该索引作为实现的一部分，避免大 space 统计退化为全表扫描。迁移不修改历史数据，不改变分片数量。
- 统计按单 space 单分片执行，明确不做跨 space、跨分片总计；后续若需求扩展为多 space 才另行设计并发聚合与部分失败处理。

### 4. 测试、文档与验收

- 控制器测试覆盖：默认从上下文取 space、显式 `space_code` 覆盖上下文、缺少可用 space 的参数错误，以及三个字段始终存在。
- 服务/仓储测试覆盖：同一 space 中 `file`、`directory`、`resource` 各一条时分别返回准确数量；空 space 返回三个 `0`；已删除节点不计入；不同 space 不串数据；历史 `is_dir = 1` 目录归类为 `directory`；未知类型按约定被拒绝或显式处理。
- 使用现有测试 fixture 重建目标分片表并插入数据，验证统计查询走 space 对应分片；补充必要的 SQL/索引迁移校验，确保所有分片表结构一致。
- 在仓库现有 API 文档承载位置补充 endpoint、参数来源、响应 JSON 示例、有效状态口径和三类字段定义；若仓库没有集中 API 文档，则在 `api` 模块现有接口说明位置新增最小示例，避免修改无关客户端。
- 验收时运行 API 模块相关单测（控制器、服务、仓储）、项目现有构建，并检查迁移 SQL 可在测试数据库执行。重点确认响应 JSON 字段固定且为数字、空 space 不缺字段、已删除/无效数据排除、space 隔离和三类节点计数与查询口径一致。

## 风险与边界

- 统计结果是查询时快照，不提供跨请求一致性或缓存承诺；并发创建/删除期间允许出现数据库事务提交时点的自然差异。
- 直接聚合 file 表与现有分页接口的根层级语义不同：本接口统计 space 内全部有效节点，包含目录后代和 resource；文档必须明确这一点，避免调用方误以为是根目录列表数量。
- 旧数据可能存在 `is_dir` 与 `node_type` 不一致；实现必须先按 `NodeType.from` 的兼容规则统一归类，并用回归测试锁定优先级，不能同时计入两个类别。
- 新索引会增加每个分片的存储和写入成本；上线前通过执行计划和现有分片规模评估，若索引创建需要在线变更，则按项目发布窗口执行并保留数据库回滚/删除索引操作。

## 验证

- 静态检查接口路径、参数、DTO 序列化字段与现有 `@FileAPI`、`/v1/files` 和错误响应约定一致。
- 用集成测试验证正常 space、空 space、三类节点分别计数、同 space 多节点累计、不同 space 隔离，以及 `status = DELETED` 和无效节点排除。
- 验证目录兼容规则：`is_dir = 1` 只计入 `directory`；正常 `file` 与 `resource` 不因闭包关系或 `is_dir` 默认值被误分类。
- 验证分片路由：使用至少两个映射到不同分片的 space，分别写入并统计，确认每次只读目标 space 对应分片且不会漏数/串数。
- 执行迁移 SQL 的测试数据库验证，检查 `file_0` 至 `file_15` 索引存在且字段顺序为 `space_code,status,node_type`；用 `EXPLAIN` 确认统计查询使用该过滤索引或有等价可接受的执行计划。
- 运行 API 模块相关测试及项目现有构建；检查新增 API 文档中的链接/锚点可达、示例 JSON 可按当前响应反序列化、示例命令参数有效，并将关键事实与当前 controller、service、repo、配置和 SQL 定义逐项比对。
