## 目标

让目录 API 只以 `file_entry` 为目录位置、父子关系和祖先链的运行时真相，删除闭包读写、双写、自动回退、模式开关以及当前构建和测试对闭包模型的依赖。公开的 `file_id`、`ancestor_id`、响应结构、列表/分页/路径/移动语义保持兼容。

本 PR 仅提交实现方案，不修改应用代码、生成代码、配置或数据库。

## 当前上下文

- 2026-11-19，需求方在本任务中确认：“生产实例已经全部切换了。不用删数据表，清理读写逻辑即可。”本方案据此解除生产是否切换的澄清阻塞，并将“不删除数据表”作为确定边界。这是需求方确认，不是本任务自行获取的生产配置快照或监控结论。
- 核查基线为 `f1f086e`。仓库默认配置和 Java 注入默认值仍允许 `closure` / `dual`，本地配置才是 `entry` / `entry`；因此生产完成切换并不等于仓库已经完成清理。
- `FileRepo` 的目录读请求仍有闭包实现；`FileEntryRepo` 的 `entryWriteEnabled()` / `closureWriteEnabled()` 决定是否写入 entry、同步闭包和允许跨空间非空目录移动；`FileService.delete` 仍调用闭包删除入口。
- `file` 继续保存文件本体及兼容缓存，`file_entry` 保存位置；删除闭包不意味着删除 `file`，也不意味着按目标空间迁移 `file` 的物理分片。

## 方案

### 决策一：接受生产已切换的确认，不额外设置固定等待期

**结论：** 将上述需求方确认记录在本方案，作为本次清理的生产切换依据；不再要求追加前轮建议的“至少 7 天”固定观察窗口，也不将不存在的发布记录写成既有事实。理由是切换已由需求方确认，且本次没有任何破坏性数据库动作。

发布检查仍需记录发布版本、实例范围、发布时间及发布前后目录错误/延迟基线；这些是 Build 的发布验证记录，不是重新询问已确认的切换事实。灰度期间覆盖创建、移动、列表和祖先链请求；任何活动文件缺 entry、祖先断链或目录 API 异常升高都暂停扩量并定位，不自动切闭包掩盖问题。保留原有值班和发布观察流程，不凭仓库推断新的生产 SLO 或业务周期。

### 决策二：主表与 16 张分片表全部保留，但不再是应用依赖

**结论：** 保留 `file_closure`、`file_closure_0` 至 `file_closure_15` 及现存数据；本次不新增 DROP、TRUNCATE、清空、回填或反向同步 SQL，不执行生产数据库操作。物理表未来是否下线另立运维任务。

**保证脱钩的方式：** 删除所有闭包查询/写入方法和调用点、jOOQ 表映射及生成入口；测试数据库仅创建当前需要的 `file` / `file_entry` 等表，不创建闭包表，完整目录回归必须仍能通过。生产残留表不能作为回退真相：entry-only 期间它们没有持续同步，表存在不代表数据可供回滚使用。

### 决策三：直接删除模式选择，默认和测试统一为唯一 entry 路径

**结论：** 不把 `read-mode` / `write-mode` 长期保留为值固定为 `entry` 的空配置。删除三份 YAML 的模式字段、环境变量占位符、Java 注入默认值、模式判断及测试 setter。其他 entry 参数（例如跨空间迁移告警阈值）保留。应用不再消费旧模式配置；部署配置中的遗留键应随发布清理，它们不能再改变目录实现。

运行时行为如下，均不是新增第二套目录实现：

| 场景 | 清理后的行为与保留边界 |
| --- | --- |
| 创建、重命名、同空间移动、删除 | 保留 `file` 与 entry 的现有事务和校验，只去掉闭包维护；删除仍由 `updateFile` 驱动 entry 删除，不误删整个删除流程。 |
| 跨空间文件、空目录、非空目录移动 | 保留现有 entry 迁移、锁顺序、防环、深度上限、IN 分块、冲突检测、规模告警和兼容缓存更新；移除仅由闭包双写引出的限制及叶子闭包搬迁。 |
| 同名校验、按父目录和名称查询、列表、分页 | 直接调用既有 entry 查询；保留过滤条件、资源节点区分、稳定游标、排序和计数，不回退旧 join，也不重写分页协议。 |
| 路径、直属祖先、批量祖先 | 直接走 entry 祖先链，继续按文件实际位置缓存确定空间；祖先 ID 保持为 `file_id`，不向外泄漏 `entry_id`。 |
| 活动的目录支持型文件或其父链缺 entry | 保留/传播 `EntryReadNotReadyException` 等显式异常；移除“捕获后读闭包”的处理及误导性注释。不懒回填、不从历史闭包重建、不将损坏伪装成成功空结果。 |
| 根目录、空目录、文件不存在或已删除 | 保留既有 entry 路径的合法空值语义：根直属祖先为空，空目录列表为空；批量祖先对不存在/已删除文件返回空数组，不能把它们一概改成数据一致性故障。 |
| 非目录支持型文件（system/temp 等） | 保留文件本体查询与 ID 映射；不新增 entry 要求。路径/祖先按既有无目录关系语义返回空结果，不再触发闭包 SQL。 |

`FileRepo.getPathFiles` / `getDirectAncestorId` 当前在文件未找到或不支持目录时仍可能落入闭包分支，实现时要显式收口这些边界，而不只是删除 `useEntryRead()` 条件。需要 entry 的活动对象异常与合法缺失结果必须分别验证；不扩大本次对外错误协议的变更范围。

### 决策四：当前依赖可删除，历史记录不可改写

以下清单覆盖基线中闭包模型、配置、开关及回退的引用。路径前缀为：`repo/` = `api/src/main/java/com/ke/bella/files/db/repo/`；`service/` = `api/src/main/java/com/ke/bella/files/service/`；`generated/` = `api/src/codegen/java/com/ke/bella/files/db/`；`tests/` = `api/src/test/java/com/ke/bella/files/`。

| 当前文件 | 处置及理由 |
| --- | --- |
| `repo/FileRepo.java` | **替换/删除**：将 `exists`、按名称 `queryFile`、`listFile`、`findFiles`、`pageFiles`、`getPathFiles`、`getDirectAncestorId`、`getFileAncestorIds` 收口到 entry；移除闭包 SQL、`fileEntryReadMode`、`useEntryRead`、setter、回退日志/捕获；删除 `createFileClosureInsert`、`addFileClosures`、`loadAncestorClosuresForUpdate`、`deleteFileClosure`、`ClosureMoveSnapshot` 及外部边删除/插入、root_depth 更新等闭包专用辅助逻辑。保留文件本体增删改、ID 归一化、分片计数与 entry 调用；辅助方法和 import 按剩余调用者清理，避免误删共享筛选能力。 |
| `repo/FileEntryRepo.java` | **替换/删除**：移除 `writeMode`、两种 enabled 判断、setter 和所有“关闭 entry 则空操作”分支；entry 写成为必经路径。删除 `moveLeafClosureAcrossSpace`、`insertClosure`、对应 imports，以及非空目录必须指定 entry 模式的旧检查；保留其他一致性、防环、事务和批量锁检查。保留缺 entry 异常，改掉要求调用方闭包回退的注释。 |
| `repo/DSLContextHolder.java` | **删除局部**：移除 `FILE_CLOSURE` import 和逻辑表至分表的映射；保留 file、entry、progress、dataset 等映射及缓存。 |
| `service/FileService.java` | **删除局部**：移除 `deleteFileClosure` 调用及因此空掉的条件；保留 `updateFile` 的 entry 删除、资源删除规则、广播顺序和异常传播。 |
| `api/src/main/resources/application.yml` | **删除模式字段**：移除 `read-mode`、`write-mode` 及 `BELLA_FILE_ENTRY_READ_MODE` / `BELLA_FILE_ENTRY_WRITE_MODE`；默认行为由唯一实现确定，保留跨空间告警阈值。 |
| `api/src/main/resources/application-local.yml` | **删除模式字段**：本地配置不再重复声明 entry/entry，防止暗示还存在可切换实现。 |
| `api/src/test/resources/application-junit.yml` | **删除模式字段**：测试与默认应用走同一 entry-only 行为，不保留 closure/dual 测试 profile。 |
| `api/pom.xml` | **删除局部**：移除 jOOQ 的 `file_closure` matcher 与 includes 表清单项；保留 `file_entry` 和其他表。生成使用受控开发数据库，不连接生产。 |
| `generated/tables/FileClosure.java`、`generated/tables/pojos/FileClosureDB.java`、`generated/tables/records/FileClosureRecord.java` | **删除**：它们是当前编译源码而非不可变历史，删除匹配生成入口后不应继续携带。 |
| `generated/Tables.java`、`generated/DefaultSchema.java`、`generated/Keys.java`、`generated/Indexes.java` | **重新生成/替换局部**：去掉闭包表常量、注册、唯一键、索引及 imports；与三份专属类一起更新，不能只删类留下编译引用。审查生成 diff，拒绝无关 schema 或格式大幅变化。 |
| `tests/db/repo/FileRepoTestFixture.java` | **替换**：不再创建/删除闭包表，只创建所需 file、entry 表；让漏网 SQL 在无闭包 schema 中直接暴露。 |
| `tests/db/repo/FileRepoMoveTest.java` | **替换**：将闭包子树、深度与查询断言改为 entry 父子链、可见性、祖先结果及缓存断言；保留同空间子树/根移动、防环、分页和失败不改变状态的覆盖，不简单删掉整类测试。 |
| `tests/db/repo/FileEntryRepoTest.java` | **替换/删除专用用例**：删 closure-only 逃生、双写和模式限制用例；将 entry-only 开关测试变成默认行为测试；将 closure/entry 对照、空间归属及跨空间闭包断言改为固定预期的 entry/API 断言。保留资源过滤、重名、游标、祖先链、跨空间非空子树、深度/分块/同物理分片隔离覆盖。 |
| `tests/db/repo/FileRepoUpdateTransactionTest.java` | **替换局部**：删除模式 setter；保留重命名/删除过程中 file 与 entry 一起回滚的验证。 |
| `tests/db/repo/FileEntryCrossSpaceMoveTransactionTest.java` | **替换局部**：删除模式设置与闭包快照；保留源/目标 entry、文件空间缓存和其他未提交修改的事务原子性断言。 |
| `tests/db/repo/FileEntryRepoCrossSpaceLockTest.java` | **删除局部**：移除 entry 模式 setter；保留跨空间迁移锁定顺序和竞争安全验证。 |
| `tests/service/FileServiceMoveTransactionTest.java` | **替换**：移除手工闭包插入、快照、存在性/深度断言及 `addFileClosures` 调用；用 entry 状态和父子链验证创建与移动竞争、更新失败回滚及失败不广播，保留原并发/事务价值。 |
| `tests/service/FileServiceResourceTest.java` | **删除局部并保留语义**：移除对 `deleteFileClosure` 的 Mockito 验证；继续验证资源文件状态/entry 删除与内容操作限制。 |
| `api/sql/14-update.20250807.sql` | **原样保留**：主表、16 分片和历史回填是已发布迁移；现有环境不重跑，历史初始化链可以仍创建保留表，但新应用不依赖它们。 |
| `.issue-flow/issues/60-issue/plan/001-implementation.md` | **原样保留**：历史闭包移动设计，不作为当前实现或验收依据。 |
| `.issue-flow/issues/63-file-entry/plan/001-implementation.md` | **原样保留**：历史双写与切换设计，不据此恢复回退。 |
| `.issue-flow/issues/71-issue/plan/001-implementation.md` | **原样保留**：历史方案记录，不要求更新其中的闭包描述。 |
| `.issue-flow/issues/75-space-node-type/plan/001-implementation.md` | **原样保留**：历史节点类型方案，不是运行时闭包依赖。 |

表内测试之外，还需回归当前无闭包直接引用的控制器测试，尤其 `FileControllerMkdirTest`、`FileControllerMoveTest`、`FileControllerPageFilesTest`、`FileControllerGetFileAncestorIdsTest` 和 `FileControllerResourceTest`；不能仅以引用文件编译通过判断行为兼容。

## 非目标

- 不删除或更改任何生产表及数据，不新增数据库迁移，不改写历史 SQL 和历史 plan。
- 不重新设计 `file_entry` 表、`entry_id` 生成、单活动入口约束、空间 hash 或分片路由；不删除兼容 file 行/字段，不迁移对象存储或改变广播协议。
- 不实现异步子树迁移、不扩充运维闭包逃生阀、不把缺失 entry 的自动修复并入请求路径。

## 发布与回滚边界

1. 实现时先让目录测试基座不建闭包表，再收口读写、配置和服务调用，最后同步更新生成入口/产物；一个 Build PR 完整交付，不发布配置和实现不一致的中间版本。
2. 发布前按上述生产确认复核本次目标环境，清理配置中心/部署中的旧模式键；记录有效版本和发布范围。此次不执行数据库迁移，也不要求复制或恢复闭包数据。
3. 灰度执行验证矩阵，扩量后继续按现有发布流程观察目录错误和延迟；显式 entry 一致性故障不得由空结果或闭包数据掩盖。
4. 代码版本回退只能退到已验证支持 entry-only 的先前版本，且必须显式保持 `read-mode=entry`、`write-mode=entry`，因为旧版本默认是 closure/dual；发布前核实可用回退制品及配置。不能仅回退镜像然后依赖旧默认值。
5. 保留表不等于可回滚到闭包。禁止将旧数据当作最新位置切回 closure/dual；故障优先停止扩量、回退 entry-only 版本或前向修复。数据修复另审，不在本方案中承诺闭包恢复能力。

## 验证

以下是 Build 必须提交的验收证据，本 Plan 阶段不宣称已运行或通过应用测试。

### 行为与故障矩阵

所有仓储/服务集成回归在**未创建闭包表**的 H2 测试基座执行；另在开发 MySQL schema 中验证关键事务和分片 SQL，以免 H2 掩盖 MySQL 差异。每项均断言外部结果及 file/entry 状态，不再与旧闭包算法比较。

| 验收点 | 必须覆盖的结果 |
| --- | --- |
| 创建/重名/重命名/删除 | 根和子目录、文件/目录/资源、删除后重建、同目录冲突与不同目录同名；失败整体回滚，成功广播及资源内容限制不变。 |
| 同空间移动 | 文件、空目录、非空子树、移到根、移入自身/后代拒绝；entry 父链与查询结果一致，冲突和事务失败不产生半迁移。 |
| 跨空间移动 | 文件、空目录、非空子树、相同和不同物理分片、目标冲突、并发锁、深度上限及大 IN 分块；源不可见、目标可见，file_id/对象位置不变，空间缓存与 entry 同事务更新。 |
| 列表/分页/路径/祖先 | 根、空目录、深层目录、资源类型过滤、现有文件属性过滤、相同时间排序、游标缺失、分页总数、根到父级顺序、直属祖先、批量祖先含不存在/已删除 ID、跨空间移动后查询。 |
| entry 异常必须显式失败 | 构造仍活跃的文件却缺 entry、缺父 entry 或祖先断链；针对同名校验、名称查询、列表/分页父目录解析、路径、直属和批量祖先逐个断言异常传播、不返回伪造成功。空目录和合法不存在结果另测；保留防环/深度错误。 |
| 事务和广播 | 注入 file 更新失败、目标 entry 写入冲突等故障，确认源/目标 entry、file 缓存和重命名/删除均回滚，失败不发送位置广播；保留既有竞争场景的有效覆盖。 |

### 构建、生成和引用验收

- 在 `api/` 执行 `mvn -Dtest=FileRepoMoveTest,FileEntryRepoTest,FileRepoUpdateTransactionTest,FileEntryCrossSpaceMoveTransactionTest,FileEntryRepoCrossSpaceLockTest,FileServiceMoveTransactionTest,FileServiceResourceTest test`，再执行 `mvn test`。记录实际通过/失败及环境依赖；不得以删除行为测试换取通过。
- 使用项目现有 `db.driver`、`db.url`、`db.username`、`db.password` 属性连接受控开发 MySQL，在 `api/` 执行 `mvn jooq-codegen:generate`。生成环境可保留历史闭包表，生成结果仍不得含闭包模型；再次生成应无额外差异。不得连接生产或把凭证写入提交。
- 在仓库根运行 `rg -n -i 'file_closure|fileclosure|read-mode|write-mode|closureWriteEnabled|entryWriteEnabled|setFileEntryReadMode|setFileEntryWriteMode|useEntryRead|logEntryReadFallback' api/src/main api/src/test api/src/codegen api/pom.xml`，预期**无匹配**（`rg` 返回 1）。再检查 `closure` / `dual` 的其余命中，逐项确认不是遗留目录实现、模式文案或测试名称；不要求误删其他领域的同名概念。
- 对全仓引用复核，只允许上表历史迁移、四份历史方案与本方案保留说明中的闭包引用。若 Build 基线出现新增引用，必须归类并补充清理，不以“未在清单中”为由遗漏。
- Review 检查没有 DROP/清空 SQL、历史迁移与历史 plan 的修改，没有公开 API、文件 ID、entry 模型或存储行为变更。生成产物、构建清单和运行时源码必须同时一致。
- 发布验收记录包含灰度请求结果、错误/延迟对比及回退制品配置；新版本目录请求不发出任何闭包 SQL。无闭包表测试证明应用不依赖残留表，生产保留表无需删除来证明脱钩。
