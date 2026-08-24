## 目标

- 引入按 `space_code` 分为 16 张表的 `file_entry` 目录项模型，让目录归属、父子位置、展示名称和入口状态从 `file` / `file_closure` 中解耦；`file_id` 继续标识文件本体，`entry_id` 标识一次可见目录位置。
- 第一阶段完成表结构、jOOQ 模型、目录项仓储、现有数据回填、创建/重命名/移动/删除双写，以及目录列表和同目录重名校验切读；现有 `file_closure` 继续维护路径和祖先查询，不删除、不降级现有 API 能力。
- 保持现有客户端协议兼容：`OpenAIFile.id`、`FileMoveOps.file_id`、`ancestor_id` 仍使用 `file_id`，`entry_id` 永久作为内部实现细节；`file.space_code` 改为由唯一活动 entry 派生的兼容缓存，使 file_id-only API 可 O(1) 定位入口空间。
- 为跨空间移动建立不迁移 `file` 物理行、不复制对象存储、保持 `file_id` 不变的仓储与事务原语；原语只同步更新 `file.space_code` 派生缓存，本阶段不开放生产调用，也不支持跨空间非空目录移动。

## 非目标

- 不删除 `file.space_code`、`file.filename`、`file.is_dir` 或 `file_closure`；这些字段在迁移期继续作为兼容字段或派生读模型。
- 不让同一 `file_id` 拥有多个活动入口；该单入口不变量永久保留。后续软链使用拥有独立 `file_id` 的 link 对象引用目标文件，不复用目标文件 entry，也不引入引用计数。
- 不改造历史 `file_id` 格式，不用 `entry_id` 反推分片，不引入 `file_entry_index`，也不让线上主读路径扫描 16 个分片。
- 不改变对象存储的 `bucket`、`path` 或实际对象；逻辑目录移动和跨空间入口迁移都不搬运文件内容。

## 当前上下文

- `api/src/main/java/com/ke/bella/files/db/FileIdGenerator.java` 使用空间 hash 生成用户文件和目录 ID；`FileRepo.queryFile(file_id)` 再从 ID 中提取 hash 路由 `file_N`，因此文件本体能够继续按原分片读取，但不能再用文件分片代表入口所在空间。
- `api/src/main/java/com/ke/bella/files/db/repo/DSLContextHolder.java` 将逻辑表映射为同一 MySQL schema 内的 `file_N`、`file_closure_N` 等物理表。当前“跨分片”是同一数据源内的跨表操作，可由现有 Spring 本地事务原子提交；若未来迁移为物理分库，必须在开放跨库移动前另行设计 outbox/saga。
- `FileRepo.exists`、`queryFile(space_code, ancestor_id, filename)`、`listFile`、`findFiles` 和 `pageFiles` 均依赖 `file_closure` 与 `file` 联查；`getPathFiles`、`getDirectAncestorId` 和批量祖先查询也默认根据 `file_id` 或 `space_code` 定位闭包分表。
- `FileService.createFile`、`mkdir`、`delete`、`moveFile` 和重命名入口当前同时修改文件行与闭包关系。上传对象发生在数据库创建之后、事务之外，上传失败通过现有删除逻辑清理数据库记录。
- #60 的目录子树闭包迁移及根目录移动已进入 `develop`。#63 直接复用现有子树防环、跨边替换和 `root_depth` 更新逻辑，不重新引入 `deleteFileClosure + addFileClosures` 的叶子算法，并以其闭包测试作为回归基线。
- 现有 `OpenAIFile` 同时返回内容字段和位置字段，分页还支持 `purpose`、`tags`、`cities` 等文件本体过滤。第一阶段保持“一个文件仅一个活动入口”的产品约束，避免在没有全局位置索引的情况下产生位置歧义或错误分页。
- `file` 的物理分片始终由 `file_id` hash 决定，修改 `file.space_code` 不搬迁文件行；因此该字段可以安全地缓存唯一活动 entry 的当前空间，但不能替代 entry 成为目录路由真相。

## 方案

### 1. 固化第一阶段模型决策

- 根目录不创建真实 `file` 或 `file_entry` 行；使用非空字段中的空串 `parent_entry_id = ''` 表示空间根，避免 MySQL 唯一索引对 `NULL` 的多值语义破坏根目录重名约束。
- 目录在第一阶段继续保留 `is_dir = 1` 的 `file` 兼容记录，目录入口的 `file_entry.type = 'dir'` 指向该记录。这样现有 `file_id`、`OpenAIFile`、广播数据和 `file_closure` 可保持兼容；目录对应的 `file` 行是否清理由独立兼容性 issue 评估，不影响 `entry_id` 保持内部实现细节。
- 新建 `entry_id` 使用独立的 `IDGenerator("entry-", SIMPLE_STRATEGY)`，不包含空间 hash。普通创建和同空间移动保留原 `entry_id`；跨空间移动时生成新的 `entry_id` 并置删源入口。`entry_id` 永久不进入公开请求、响应、广播或调用方持久化契约，位置操作始终用 `file_id` 寻址。
- 现有 API 中 `ancestor_id` 仍是父目录的 `file_id`。仓储层用 file 行的 `space_code` 兼容缓存和 `file_id` 解析唯一活动 entry，根目录映射为空串；跨空间公开 API 只需给现有请求增加 `target_space_code`，无需 `source_space_code` 或 `entry_id`。
- 目录项负责 `space_code`、`parent_entry_id`、`filename`、`type` 和入口审计字段；文件内容、存储位置、内容版本、解析产物、广播状态、purpose/metadata/tags/cities 等继续来自 `file`。兼容响应只用 entry 覆盖 `filename`、`isDir` 和 `spaceCode`，`createdAt`、文件审计和内容字段仍保持当前 `file` 语义。
- `file.space_code` 与 `file.filename` 定义为单入口阶段的派生兼容缓存：entry 写入成功后在同一事务内同步更新，file_id-only API 可先按 file ID 路由文件行，再用缓存值 O(1) 定位 entry；所有目录查询、重名约束和移动决策仍以 entry 为准。

### 2. 新增可重复部署的表结构与 jOOQ 模型

- 在新的顺序 SQL 迁移中创建基础表 `file_entry`，再 `CREATE TABLE file_entry_0 ... file_entry_15 LIKE file_entry`。主要字段为：自增 `id`、`entry_id varchar(96)`、`space_code varchar(128)`、`parent_entry_id varchar(96) NOT NULL DEFAULT ''`、`file_id varchar(256)`、`filename varchar(512)`、`type varchar(16)`（本阶段为 `file` / `dir`，预留 `link`）、`status` 和现有统一审计字段。
- 为兼容 MySQL 5.7 且允许同名入口被多次删除后重新创建，增加生成列 `active_flag = IF(status = 0, 1, NULL)`，建立 `uk_space_parent_name_active(space_code, parent_entry_id, filename, active_flag)`；多个已删除入口因 `active_flag IS NULL` 可共存，活动入口仍由数据库保证唯一。`entry_id` 长度限制为 96，确保该 utf8mb4 组合索引不超过 InnoDB 3072 字节上限。
- 同时建立 `uk_space_entry(space_code, entry_id)`、`idx_space_parent_status(space_code, parent_entry_id, status)`、`idx_file_id(file_id)` 和 `idx_parent_entry_id(parent_entry_id)`。不建立外键，延续现有分表和在线迁移方式，但所有写入必须做文件、父入口和空间一致性校验。
- 更新 `api/pom.xml` 的 jOOQ `includes` 和 matcher，使 `file_entry` 生成 `FileEntry`、`FileEntryRecord`、`FileEntryDB` 并实现现有 `Operator` 审计接口；更新 `DSLContextHolder`，将 `FILE_ENTRY` 映射到 `file_entry_<space hash>`。
- 新增独立 `FileEntryRepo`，复用并公开统一的空间 hash 计算，避免 `FileRepo` 和目录项仓储各自复制分片算法。所有目录项 API 必须显式接收 `spaceCode`，包括按入口、父目录、文件和名称的查询。

### 3. 回填现有目录关系并建立可核对基线

- 提供按分片、按主键区间执行的幂等回填脚本或运维入口，不在 DDL 发布事务中一次性扫描全表。只回填 `status = 0` 且需要目录支持的用户文件/目录；临时文件和系统文件继续不进入目录模型。
- 现有入口使用确定性 ID `entry-legacy-<sha256(space_code + ':' + file_id)>`，使重复执行、失败重试和双写并行期间不会生成不同入口。父入口由 `file_closure.depth = 1` 找到父 `file_id` 后使用同一算法换算；`root_depth = 1` 的节点写入空父入口。
- 回填复制当前 `filename`、`is_dir`、状态和审计字段。写入前后按分片核对：活动用户文件数与活动入口数、每个文件恰好一个活动入口、根节点/直接父节点映射、目录类型、孤儿父入口和活动同名冲突。
- 增加差异查询，逐空间比较旧闭包读出的直属子项集合与 `file_entry` 子项集合；任何缺失、重复、父级不一致或类型不一致都阻止切读。脚本输出分片、空间和文件 ID，支持修复后只重跑失败区间。
- 双写窗口内任何 entry lookup miss 都不能退回只写旧模型。新增 `ensureLegacyEntry(space_code, file_id)`：依据现有 `file` 与 `file_closure` 快照，从命中节点沿直属父链回溯到根，再按根到叶顺序使用确定性 legacy ID 幂等 upsert 缺失 entry；创建子文件时对尚未回填的父目录执行同一流程，rename/move/delete 时先补齐自身及父链。
- on-demand 创建与离线回填必须复用同一 ID、字段映射和冲突校验。并发 upsert 命中既有 entry 后重新读取并校验空间、父级、file ID、名称和类型；无法从旧模型构造完整父链或发现冲突时使当前业务事务失败并报警，不允许生成孤儿 entry。

### 4. 以 `file_entry` 为真相源重构写链路

- 创建文件和目录时，在同一数据库事务内依次写 `file`、活动 `file_entry`、派生 `file_closure`；任一步失败整体回滚。上传对象失败时沿用现有清理入口，同时删除/置删对应 entry 和 closure，避免留下可见空文件。
- 重命名时先在 entry 分片按 `(space_code, parent_entry_id, filename)` 加锁并更新 `file_entry.filename`，再双写 `file.filename` 维持单入口兼容；`file.filename` 继续作为唯一活动 entry 名称的派生兼容缓存。
- 删除时以 entry 为入口：将活动 entry 置删并删除其派生 closure，再按第一阶段“一个文件一个活动入口”约束置删 `file`。仓储中保留活动入口计数保护，若发现多入口则按数据不变量破坏处理：拒绝删除文件本体、记录告警并要求先修复异常数据。
- 同空间移动更新原 entry 的 `parent_entry_id` 并保留 `entry_id`，随后调用 #60 的子树闭包迁移逻辑；名称锁、同名校验、防移动到自身/后代和 entry/closure 写入处于同一事务。
- 跨空间移动原语接收 `file_id`、目标空间和目标父目录：先按 `file_id` 路由并锁定 file 行，从其 `space_code` 派生缓存得到源空间，再锁定源 entry 与目标名称；随后向目标分片插入新 `entry_id`、创建目标 closure、置删源 entry、移除源 closure，并在同一事务内把 `file.space_code` 更新为目标空间。file 物理行、`file_id`、bucket/path 和对象内容均不变化。
- 第一阶段原语只允许普通文件和空目录；非空目录转交 issue #67（后调整为单事务同步迁移，见该 issue 现行正文）。同步原语曾受独立配置 `bella.file-api.file-entry.cross-space-move-enabled` 控制；该开关已随 #67 交付移除，跨空间能力默认可用。
- 现有 `/v1/files/move` 第一阶段继续维持同空间公开语义；后续公开普通文件/空目录跨空间移动时，仅增加可选 `target_space_code`，源空间继续由 file 缓存解析。这样不扫描分片，也不把缓存字段提升为入口真相。
- 所有广播在 file、entry、closure 事务成功后沿用现有事件类型发送。位置事件中的公开对象继续返回 `file_id`；内部日志补充 source/target space 和 entry ID，便于核对和重试。

### 5. 分阶段切换目录读路径

- 增加默认关闭的配置：`bella.file-api.file-entry.write-enabled`、`read-mode=closure|compare|entry`（独立的 `cross-space-move-enabled` 已随 #67 交付移除）。DDL 发布后先开启双写，旧 closure 仍是线上读源；回填完成后进入 compare 模式，主响应仍来自旧模型，只记录集合、父级和重名判断差异。
- 将 `exists`、按目录和名称查询、`listFile`、`findFiles` 改为先按 `space_code + parent_entry_id` 查询 entry，再按 `file_id` 路由分组批量获取文件本体，最后按 entry 顺序合并，并用 entry 覆盖位置字段。禁止用目标空间直接假设所有 `file_id` 都位于同一 `file_N`。
- `pageFiles` 仍需保持 purpose/tags/cities 等文件字段过滤和精确总数。第一阶段在“入口与文件同空间、每文件单入口”的公开约束下，以 entry 作为目录条件并在同一物理分片 join `file` 完成过滤和计数；在跨空间公开移动前，另行实现跨分片分页索引或调整 API，不采用分页后内存过滤。
- 将 `getDirectAncestorId`、路径和祖先服务内部签名补充 `space_code`，先用 file 缓存定位 entry 和 closure 空间。公开 API 返回的祖先 ID 仍为目录 `file_id`，从而保持现有 `FileAncestorIdsOps` 和路径输出兼容；`getPathFiles` 在完成按 ancestor file ID 分组 hydration 前仍不能读取跨空间入口。
- entry 模式稳定后，目录列表和重名校验不再把 closure 当真相源；但创建、移动、删除仍继续维护 closure，`info`、祖先链和批量 ancestor 查询继续从 closure 读取。

### 6. 固化单入口与后续路线

- #63 交付基础表、回填、双写、核心目录切读、同空间移动兼容和跨空间事务原语，达到“文件本体不因入口迁移而变化”的数据模型基础。
- 后续 issue A 改造 `pageFiles`、`getPathFiles/info` 等同分片 join，并在现有 move 请求中仅增加 `target_space_code`，随后开放普通文件和空目录的同步跨空间移动；`entry_id` 不进入协议。
- 后续 issue B' 如有共享需求，采用独立软链对象：`type='link'`，软链拥有自己的 file 行、`file_id` 和唯一 entry，并通过 `target_file_id` 指向目标。第一版仅允许链向普通文件、禁止链到链，目标目录子树不物化进 link 的 closure；位置操作使用软链自己的 `file_id` 寻址，删除软链不影响目标，无需引用计数。
- 只有产品明确需要硬链/挂载语义，即多个位置共享同一文件生命周期且修改一处处处生效时，才重新评估多入口、引用计数和位置级 API；在此之前不排期原多入口方案。
- 后续 issue C 为 #67：非空目录跨空间迁移。方案已调整（原设想为异步任务 + 分批幂等复制）：闭包切换为 entry 单写后只需迁移窄表 `file_entry`，改为单个本地事务内批量改写子树 entry 的 `space_code`（及根 entry 的 `parent_entry_id`）并刷新 `file.space_code` 缓存；迁移前统计子树规模，超过可配置阈值只记 warning 不拒绝，告警数据用于评估是否需要异步 Phase 2。
- 后续顺序固定为 A（文件/空目录同步跨空间 API）→ B'（软链独立对象）→ #67（非空目录跨空间单事务迁移）。closure 是否替换为 `file_entry_closure` 或增加反向索引，继续根据上述能力的真实查询压力单独立项。

## 迁移

1. 基于已合入的 #60 闭包子树移动实现，发布 `file_entry` DDL、jOOQ 代码和全部默认关闭的配置；验证 16 张分表、索引和映射均可用。
2. 开启 entry 双写但保持 `read-mode=closure`，观察新创建、目录、重命名、移动、删除的 entry/closure 事务成功率；未回填对象通过 `ensureLegacyEntry` 现场补齐，任何补齐或 entry 写失败都回滚原写，不允许静默降级为只写旧模型。
3. 分片分批回填历史活动数据，反复运行计数、父级、重名和集合差异核对，直至全量一致；期间新写由确定性查重和活动唯一索引避免重复。
4. 切换 `read-mode=compare`，对 exists、列表、父级和路径入口做采样双读，旧结果对外返回，差异进入指标和结构化日志。
5. 先按小流量空间切 `read-mode=entry`，再逐步扩大；列表、重名和分页稳定后完成全量切换，closure 继续双写并承担祖先查询。
6. `pageFiles`、`getPathFiles/info` 的跨分片 hydration 完成并通过回归后，跨空间移动随 #67 交付默认开放（原独立开关已移除）。非空目录由 #67 以单事务同步迁移实现（要求 write-mode 已切 entry）。

## 风险与边界

- `file_entry` 活动唯一索引不能直接使用 `(space_code, parent_entry_id, filename, status)`，否则第二次删除同名文件会与历史 `status=-1` 行冲突；生成列只约束活动行，并需在目标 MySQL 5.7+ 环境验证索引长度和执行计划。
- `file_closure` 以 `file_id` 表达路径，因此本方案永久保持每个 `file_id` 一个活动 entry；软链使用新的 link file ID，不复用目标 file ID，避免 closure 主键和路径语义冲突。
- `file.space_code` 必须与唯一活动 entry 同事务更新并接受一致性核对。它只用于 file_id-only API 的 O(1) 入口空间解析；目录列表、重名、父子关系和移动校验不得只信任该缓存，发现缓存与 entry 不一致时拒绝位置写并报警。
- `pageFiles` 的 entry⋈file 同分片 join 和 `getPathFiles` 的 closure⋈file 同分片 join 与跨空间入口互斥：任何跨空间移动都会让 entry/closure 与 file 物理行落在不同分表。相关查询已完成按 file ID 分组 hydration，跨空间入口随 #67 默认开放，不再有独立开关。
- 双写先于全量回填时，lookup miss 必须触发确定性 on-demand 父链补齐。若实现缺失该 fallback，存量目录下创建及存量对象 rename/move/delete 都会产生部分双写，属于阻断发布的问题。
- 当前跨分片原子性依赖所有分表位于同一 MySQL 数据源。若部署拓扑改变为物理分库，跨空间开关必须保持关闭，并先补充事务日志、幂等状态机和补偿任务。

## 回滚

- 读路径异常时立即把 `read-mode` 从 `entry` 或 `compare` 切回 `closure`；closure 在整个迁移期持续同步，因此无需反向回填即可恢复原目录列表、重名和路径行为。
- 回填或数据核对失败时停止后续批次，保留已写 entry，修复脚本后按分片/主键区间幂等重跑；不删除已完成数据，也不影响旧读路径。
- 双写版本出现事务或性能问题时先回滚应用版本。由于 entry 与旧模型同事务提交，不会存在“旧写成功、entry 失败”的新数据；已创建的 `file_entry_*` 表和列保留，不在紧急回滚中执行破坏性 DDL。
- 跨空间内部原语失败由本地事务整体回滚源/目标 entry、closure 和 `file.space_code` 缓存；若未来物理分库，此回滚结论不再成立，必须在新的设计审批后才能启用。

## 验证

- **DDL 与生成代码**：在项目声明支持的 MySQL 5.7+ 环境执行全量初始化和增量迁移，确认 16 张表、生成列、活动唯一索引和查询索引可创建；运行 jOOQ generate，确认 `FileEntryDB/Record/Table`、matcher 和 `DSLContextHolder` 映射正确，`mvn test` 编译不依赖手写生成类。
- **仓储与事务**：新增 `FileEntryRepo` 集成测试覆盖空间 hash 与现有算法一致、根目录、子目录、活动重名、重复删除后重建、按 entry/file 查询、同空间移动保留 entry ID、跨空间移动重建 entry ID、`file.space_code` 同事务更新、目标冲突、事务失败整体回滚，以及 file 物理分片、`file_id`、bucket/path 和对象内容未变化。
- **创建与删除**：覆盖新上传、mkdir、对象上传失败清理、文件删除、空目录删除和发现多活动入口时拒绝误删文件本体；断言 file、entry、closure 三者状态一致。
- **双写 fallback**：构造父目录、自身或完整父链尚未回填的存量数据，分别执行 create、rename、move、delete；验证 `ensureLegacyEntry` 从根到叶幂等补齐、并发调用汇合到同一 legacy ID、离线回填不产生重复，旧模型不完整或冲突时业务事务整体失败。
- **移动与重命名**：在 #60 子树测试基础上覆盖文件、空目录、非空目录同空间移动、防环、同名冲突、重命名双写；跨空间仅覆盖普通文件和空目录的内部原语，验证默认开关拒绝、显式开启后源入口不可见、目标入口可见、`file.space_code` 指向目标且 `file_id/bucket/path` 不变；非空目录支持由 #67 交付（单事务子树迁移，仅限 write-mode=entry）。
- **读路径兼容**：对根目录、子目录、空目录、名称查询、exists、普通列表和分页执行 closure/entry 双读，比较顺序、分页总数、过滤结果和 `OpenAIFile` JSON；批量文件补充需按 `file_id` 路由分组，避免目标空间分片假设。
- **路径与祖先**：复用并扩展 `FileControllerGetFileAncestorIdsTest`、目录信息和 #60 移动回归，确认 entry 切读后路径、直属父级、祖先链、批量 ancestor 和 `root_depth` 均无回退；增加跨空间数据证明旧 join 会漏行，并以此测试锁定跨空间开关在查询改造完成前不得开启。
- **回填与灰度**：构造含根节点、深层目录、历史删除和同名重建的数据集，验证回填幂等、断点重跑、差异报告和修复；演练 `closure -> compare -> entry -> closure` 配置切换，确认回滚不需要数据恢复。
- **性能**：对每个主要 SQL 做 `EXPLAIN`，确认目录列表使用 `idx_space_parent_status`、文件反查使用 `idx_file_id`、活动重名命中唯一索引；以典型大目录数据量比较旧闭包列表与 entry 列表的延迟、扫描行数和批量 hydration 次数，避免 N+1 查询。
