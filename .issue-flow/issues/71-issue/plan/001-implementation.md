## 目标

- 将非文件业务资源建模为拥有独立节点 ID 的资源引用节点，复用空间、目录、重名校验、移动、重命名、移除和祖先路径能力；节点只保存业务资源定位信息，不创建对象存储内容，也不代理业务系统的权限与生命周期。
- 为统一资源视图提供稳定协议：所有树节点返回 `node_type`，取值为 `file`、`directory` 或 `resource`；资源节点额外返回 `resource_type`、`resource_id`、可选 `resource_source`、`resource_url` 和 `resource_status`，调用方不再依赖 MIME、扩展名或 `is_dir=false` 猜测资源类别。
- 保持现有 OpenAI File API 和物理文件行为兼容：既有文件、目录 ID、`object=file`、`is_dir`、上传、下载、预览、重新上传、解析、进度和对象存储路径不改变；资源节点不会进入仅面向 OpenAI 文件内容的列表和处理链路。
- 管理端在同一目录列表中展示文件、目录和资源引用，资源支持打开外部地址、重命名、移动和移除，但不会出现上传、重新上传、下载、预览或内容解析入口。

## 非目标

- 不复制、缓存、解析或转换业务资源内容，不把 `resource_url` 当作文件下载地址或对象存储路径。
- 不校验或同步业务系统中的资源权限、状态和删除操作；访问资源时仍由目标业务系统鉴权。
- 不在第一阶段引入资源类型注册中心、业务对象外键、跨系统级联删除、后台有效性探测或资源内容搜索。
- 不允许资源引用作为目录拥有子节点，也不在第一阶段支持同一节点的多目录挂载；同一业务资源需要出现在多个目录时，创建多个彼此独立的引用节点。

## 当前上下文

- 当前 `develop` 使用 `file` 分片表保存文件和目录，并用 `file_closure` 表达树关系；`FileType` 通过 ID 后缀决定用户分片和目录能力，`OpenAIFile` 与管理端 `KnowledgeFile` 主要依赖 `is_dir`、MIME 和扩展名区分节点。
- `FileService.mkdir` 已证明“无对象存储内容的 `file` 行”可以参与创建、查询、广播和闭包管理；移动、重命名、同目录重名和删除入口也已同时支持普通文件与目录。因此资源引用复用现有节点身份和目录操作，比引入第二套资源树、跨表合并分页及双套闭包更符合当前实现边界。
- #63 已进入 `flow::approve`，其方案和待合入实现引入 `file_entry` 作为目录位置真相源，并继续以独立 `file_id` 对象承载文件、目录以及后续 link 类节点。#71 的 Build 必须在 #63 合入后从最新 `develop` 重放：资源身份和业务字段落在 `file` 对象，`file_entry.type=resource` 表达可见入口，`file_closure` 继续以该资源自己的 `file_id` 维护祖先关系；不得在 #63 之外再实现一套资源目录表。
- 当前 `/v1/files` GET、`/{file_id}` 的 `get_url`、内容、预览、裁图、进度、DOM tree、PDF 和 PUT 重传等路径会进入对象存储或内容处理；资源节点必须在任何存储调用之前被统一拒绝或跳过，不能依赖空 `bucket/path` 让底层偶然失败。
- 管理端双击非目录节点会进入预览，操作菜单对全部 `is_dir=false` 节点开放重新上传；仅增加资源字段而不调整交互会把资源误当文件，因此前后端都必须以 `node_type` 判定能力。

## 方案

### 1. 扩展节点身份与资源引用字段

- 在 `FileType` 增加用户空间类型 `RESOURCE("resource", "-r")`，由 `FileIdGenerator` 生成带 `-r` 后缀的稳定节点 ID；将其纳入 `isUsersType()` / `needsDirectorySupport()`，但不纳入普通用户文件或目录判断。这样现有按 ID 路由、祖先查询、重命名和移动可以复用，同时内容入口能够在查询数据库前后明确识别资源类型。
- 在逻辑 `file` 及全部物理表新增：
  - `node_type varchar(16) NOT NULL DEFAULT 'file'`：`file`、`directory`、`resource`，作为对象身份真相源；现有普通文件回填 `file`，`is_dir=1` 的现有行回填 `directory`。
  - `resource_type varchar(64) NOT NULL DEFAULT ''`：业务侧资源类别，由接入方定义，例如 `dataset`、`workflow`。
  - `resource_id varchar(256) NOT NULL DEFAULT ''`：业务系统中的资源标识，按不透明字符串保存，不做数值或 UUID 假设。
  - `resource_source varchar(128) NOT NULL DEFAULT ''`：可选来源/命名空间，用于区分不同业务系统中的同类型同 ID 资源。
  - `resource_url varchar(2048) NOT NULL DEFAULT ''`：可选跳转地址，只允许绝对 `http`/`https` URL；为空时管理端只展示定位信息，不提供打开动作。
  - `resource_status varchar(16) NOT NULL DEFAULT ''`：资源节点使用 `active` 或 `invalid`；普通文件和目录保持空串，避免把外部状态混入现有 `file.status` 删除语义。
- 为 `(space_code, node_type, resource_source, resource_type, resource_id, status)` 建普通查询索引，不建立业务身份唯一约束。现有 `file_entry` 活动同目录名称唯一约束继续防止同目录同名；同一外部资源允许在不同目录或同目录不同展示名下登记多个引用，删除任一引用不影响其他引用。
- #63 的 `file_entry.type` 扩展为 `file`、`dir`、`resource`；资源创建时 `file.node_type=resource`、`file.is_dir=0`、`file_entry.type=resource`，`bucket/path/mime_type/type/extension` 为空、`bytes=0`。仓储读取时校验对象与入口类型一致，类型不一致视为数据损坏并拒绝返回，避免错误进入内容能力。
- 更新 SQL 全量初始化、增量迁移、jOOQ includes/matcher 和生成模型。增量脚本先加可空或带默认字段，再分片回填现有目录的 `node_type`，确认无空值与类型冲突后再收紧约束；回填不改 `file_id`、对象存储字段、`file_entry` 或 `file_closure` 关系。

### 2. 定义资源创建、查询和生命周期接口

- 在 `/v1/files/resources` 增加 JSON 创建接口，请求包含 `name`、可选 `ancestor_id`、必填 `resource_type`、`resource_id`，以及可选 `resource_source`、`resource_url`、`description`、`tags`、`cities`。名称、描述、标签、城市和空间/祖先校验复用现有目录与文件规则；`resource_type/source/id` 去除首尾空白后不能为空或超长，URL 在写入前验证协议和长度。
- 创建流程在现有同目录名称锁内执行，并在同一数据库事务中写入资源 `file` 对象、活动 `file_entry` 和派生 `file_closure`；任一步失败整体回滚。资源不执行临时文件落盘、对象存储上传、文件分片计数、内容版本初始化、解析任务或进度记录。
- `OpenAIFile` 增加 `nodeType`、`resourceType`、`resourceId`、`resourceSource`、`resourceUrl`、`resourceStatus`，沿用项目 Jackson snake_case 输出。现有字段全部保留；资源节点仍返回 `object=file`、`is_dir=false`、`bytes=0`，新客户端必须以 `node_type` 判断，旧客户端不会因字段新增而反序列化失败。
- `/v1/files/find`、`/v1/files/page`、目录下的通用列表、名称查询、详情、路径和祖先 ID 查询默认返回三类节点。分页 `type` 参数在继续接受旧值 `dir`、`file` 的同时增加 `resource`，并新增可选 `resource_type`、`resource_source`、`resource_id`、`resource_status` 过滤；目录优先排序保持不变，非目录节点再按现有时间和 ID 排序，避免改变旧分页的稳定键。
- OpenAI 兼容 GET `/v1/files` 明确增加 `node_type != resource` 条件，仅排除资源，并保留当前普通文件、目录、purpose 和排序行为；`after` 游标继续接受现有结果集中的文件或目录，但拒绝资源 ID。POST 上传和现有文件详情响应保持原语义，新增字段对普通文件分别返回 `node_type=file` 和空资源字段。
- 新增 `PATCH /v1/files/resources/{file_id}`，只允许资源节点更新 `resource_url`、`resource_status`、描述、标签和城市；展示名称仍走现有 rename，位置仍走现有 move。`resource_source`、`resource_type`、`resource_id` 创建后不可变，避免一次更新把审阅、广播或缓存中的引用静默改指向另一业务对象；需要改业务身份时先移除旧引用再创建新引用。
- 外部资源失效由业务系统或管理员显式将 `resource_status` 改为 `invalid`。失效节点仍保留在目录中、可重命名/移动/移除，但管理端禁用打开并显示失效状态；恢复时可改回 `active`。本系统不根据 HTTP 可达性自动改变状态。
- DELETE 继续删除本地节点及其 entry/closure，不调用外部系统，也不访问 `resource_url`。资源节点不允许成为祖先，因此删除无需业务资源级联；若数据异常地存在后代，沿用目录非空保护并拒绝删除，不能遗留孤儿 closure。

### 3. 统一目录操作并隔离内容能力

- 重命名、移动、exists、路径和祖先查询复用 #63 的 entry 真相源。资源重命名只改展示名称及兼容缓存，不根据名称计算扩展名；移动只改 entry/closure 位置，不改业务身份、资源状态或外部资源。
- 将节点能力判断集中为服务层方法或枚举，而不是在各控制器继续散布 `!is_dir`：至少提供 `isDirectory`、`isContentBackedFile`、`isResource`。所有内容型入口在调用 `StorageService`、DOM/PDF/裁图服务或进度仓储前要求 `isContentBackedFile=true`。
- 对资源调用上传覆盖、PUT 重传、下载/内容流、预签名 URL、预览 URL、裁图、DOM tree、PDF、内容解析和进度接口时返回稳定的 400 业务错误，错误说明该节点不具备文件内容；不得把 `resource_url` 填到现有 `url` 字段，也不得把外部 URL 传入文件预览器。
- `/v1/files/{id}?get_url=true` 和 `/v1/files?get_url=true` 只对物理文件生成对象存储 URL；通用详情中的资源跳转地址仅通过 `resource_url` 暴露。这样旧 `url` 字段继续表示文件内容地址，避免调用方把外部网页当作可下载文件。
- 描述、标签、城市和 metadata 属于本地节点元数据，可用于资源；文件版本、purpose 驱动的内容分类、dom/pdf 派生产物和 progress 不适用。资源创建不接受 purpose，内部保持空串，通用查询不得依赖 purpose 才能看到资源。
- 广播继续使用现有 envelope 和投递状态，但新增 `resource.created`、`resource.updated`、`resource.deleted` 事件，数据体为扩展后的 `OpenAIFile`。资源创建、身份外元数据更新、重命名、移动、状态变更和移除不得发布 `file.*`，避免现有文件解析或同步消费者误处理；事件只描述本地引用变化，不宣称外部资源被创建、修改或删除。
- 权限沿用现有 Bella 空间上下文、祖先空间一致性和文件节点操作权限。打开 `resource_url` 时前端只做新窗口导航，不转发 File API 凭证、不在服务端抓取 URL，目标业务系统自行完成登录和授权。

### 4. 更新管理端统一资源体验

- 扩展 `KnowledgeFile` 和请求层类型，所有判断改用 `node_type`：目录可双击进入；物理文件可双击预览；资源双击或“打开资源”仅在 `resource_status=active` 且存在合法 `resource_url` 时新窗口打开，否则不发起预览请求。
- 列表名称列为资源使用独立图标，并显示资源类型、来源和失效标记；“类型”列优先显示 `资源 · <resource_type>`，大小列对目录和资源留空，筛选项同时包含文件夹、资源和现有文件扩展名/MIME 类型。
- 操作菜单按能力显示：三类节点都可重命名、移动、移除；只有物理文件显示重新上传和预览/下载类操作；资源按条件显示“打开资源”，失效或无 URL 时展示禁用原因。删除确认文案改为“移除资源引用”，明确不会删除业务系统中的原始资源。
- 增加“登记资源”入口和表单，收集名称、类型、业务资源 ID、可选来源和 URL，并复用当前目录作为 `ancestor_id`。创建成功后刷新当前目录；第一阶段不提供资源类型下拉注册表，使用受长度限制的文本值，避免将通用能力绑定到 dataset 等具体模型。
- 移动目录选择器继续只允许 `node_type=directory` 的节点作为目标；资源节点不可进入目录栈，也不可作为祖先 ID。前端限制只改善体验，后端仍执行相同类型和空间校验。

### 5. 迁移与发布顺序

1. 等待 #63 Build 合入并从最新 `develop` 创建 #71 Build 分支；先确认 entry 双写/切读配置和表结构与已批准方案一致，再调整 #71 的 SQL 序号及仓储改动。
2. 发布向后兼容 DDL、jOOQ 模型和现有数据 `node_type` 回填；在应用创建资源前核对各分片普通文件、目录计数与回填计数一致，并保留旧字段和索引。
3. 发布后端协议、资源 CRUD、统一查询和内容能力守卫。资源创建能力先通过独立配置 `bella.file-api.resource.enabled=false` 默认关闭，但所有读取代码必须能安全识别已存在资源行，便于灰度和回滚。
4. 发布管理端类型、登记表单和按能力渲染；确认前后端均已部署后再按环境开启资源创建。若存在多实例，开关需统一配置，避免部分实例可创建而其他实例不能正确处理更新。
5. 观察创建/查询错误率、资源事件消费失败、内容接口对资源的拒绝计数、entry/object 类型不一致告警和目录查询性能；稳定后再全量开启。

## 风险与边界

- #71 依赖仍在审批中的 #63。若 #63 的最终表结构、单入口约束或读写切换策略在审批中变化，Build 必须按已合入实现调整本方案的字段落点，但保持“资源拥有独立对象 ID、entry 表示位置、内容能力显式隔离”的不变量；不得直接从当前 `develop` 并行落地 closure-only 版本。
- `is_dir=false` 对旧客户端不足以区分资源和文件，因此统一树查询中的旧客户端可能把资源渲染成文件；后端内容守卫保证不会误读存储，管理端与正式调用方必须升级为使用 `node_type`。OpenAI 兼容列表排除资源，控制旧生态暴露面。
- 资源 URL 可能指向不可信站点。后端只保存合法的绝对 HTTP(S) URL，前端使用 `noopener,noreferrer` 新窗口打开，不内嵌、不服务端抓取，也不把 Bella 鉴权信息拼入 URL；更严格的域名白名单应由部署配置或后续资源类型注册能力提供。
- 业务身份不唯一是有意设计，用于多目录引用；调用方若需要幂等登记，应先按 `space_code + resource_source + resource_type + resource_id` 查询并自行选择已有节点，服务端不能以全局唯一约束阻止合理的多入口组织方式。
- `resource_status=invalid` 是本地提示而非权威业务状态；即使标记 active，目标资源仍可能被删除或无权限。打开失败不能自动删除引用，避免瞬时网络或权限问题造成不可逆目录变化。

## 回滚

- 发现创建或事件问题时先关闭 `bella.file-api.resource.enabled`，停止新建和资源专用更新；通用查询与内容守卫继续保留，使已创建资源仍可安全查看、移动和移除。
- 应用版本可回滚到不创建资源的版本，但在仍有活动资源节点时不得回滚到完全不认识 `node_type=resource` 的读版本，否则管理端和内容入口会把资源当普通文件。回滚前应保持兼容读取补丁，或先导出并移除活动资源引用。
- DDL 使用新增列和索引，不在紧急回滚中删除；已有文件/目录 ID、entry、closure 和对象存储内容均未迁移。资源行需要清理时通过正常删除语义移除本地引用，不直接批量删除外部业务对象。

## 验证

1. **DDL 与模型**：在项目支持的 MySQL 5.7+ 环境分别执行全量初始化和增量迁移，确认逻辑表、所有文件分片及 #63 的 entry 分片字段/索引一致；运行 jOOQ generate 后编译，核对现有目录全部回填为 `directory`、其余历史对象为 `file`，文件 ID、entry 和 closure 数量不变。
2. **创建与查询**：新增控制器/服务/仓储测试，覆盖根目录和子目录创建资源、必填/长度/URL 校验、父节点不存在或非目录、跨空间父节点、同目录同名冲突、同一业务身份多目录引用、事务任一步失败回滚，以及按 ID、目录、路径、祖先和业务身份过滤查询。
3. **混排与分页**：扩展 find/page/list 仓储及控制器测试，构造目录、普通文件和资源混排，断言 `node_type` 与资源字段稳定、目录优先和时间/ID 排序稳定、`type=dir|file|resource` 兼容、总数正确；断言 OpenAI GET `/v1/files`、purpose 过滤和 after 游标保持现有文件与目录结果，只排除资源，既有 JSON 字段和值不变。
4. **目录操作语义**：扩展 rename/move/delete 测试，确认资源重命名不生成扩展名，移动后旧父目录不可见、新父目录可见、祖先链正确，失效资源仍可移动和移除；删除只清理本地 object/entry/closure 并发布 `resource.deleted`，不调用 StorageService 或任何外部 URL。
5. **内容隔离**：为 GET URL、content、preview、PUT 重传、裁图、DOM tree、PDF、progress 等入口建立资源参数化测试，断言全部在存储/解析 mock 调用前返回明确 400；同时保留普通文件成功和目录原有失败用例，证明守卫没有改变文件上传、下载、预览、重新上传和解析行为。
6. **广播与元数据**：验证资源创建、重命名、移动、状态更新、标签/描述/城市更新和删除只发送 `resource.*`，payload 包含节点及业务标识，失败回调仍更新广播状态；普通文件和目录继续发送现有 `file.*`。
7. **管理端行为**：为类型判断与菜单能力提取纯函数并通过 TypeScript 类型检查和人工场景覆盖三类节点、active/invalid、URL 有无与非法 URL；执行 `cd web && npm run type-check && npm run lint:check && npm run build`，人工验收混排图标、筛选、资源登记、外部打开、失效提示、移动目标限制和“移除引用”文案，确认资源双击不请求 preview，文件双击和目录导航保持原样。
8. **后端回归**：运行资源相关定向测试后执行 `cd api && mvn test` 与项目现有代码风格检查；重点回归 `FileControllerMkdirTest`、`FileControllerMoveTest`、`FileControllerPageFilesTest`、`FileRepoMoveTest`、#63 的 entry 读写/回填/事务测试，以及 OpenAI 文件上传、列表、详情、内容和删除路径。
9. **灰度验收**：关闭开关时创建接口不可用且已有文件/目录完全无变化；开启后登记一个带 URL 和一个无 URL/invalid 的资源，确认统一列表和业务身份查询可定位，前者只通过显式打开动作跳转、后者不会触发网络访问；关闭开关后已存在资源仍可查询和安全移除。
