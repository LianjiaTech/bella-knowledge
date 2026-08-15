## 目标

扩展 `POST /v1/files/mkdir` 的创建请求，使目录创建时可以一次性接收并保存与文件节点一致的 `metadata`、`cities`、`tags` 元数据；保持现有 `name`、`ancestor_id`、`description`、`purpose` 行为和未传入新增字段时的默认值不变。

## 当前上下文

- `FileSystemOps.MkdirOp` 当前只定义 `ancestorId`、`name`、`description`、`purpose`；Spring/Jackson 已通过全局 snake-case 配置承接 `ancestor_id` 等公开字段。
- `FileController.mkdir` 已负责目录名、描述、purpose、祖先目录和重复目录校验；文件上传接口在同一 Controller 中对 `cities`、`tags` 复用 JSON 序列化后的总长度限制（512 字符）。
- `FileService.mkdir` 已写入目录的统一 `FileDB` 记录，但当前将 `metaData` 固定为 `{}`、`cities` 和 `tags` 固定为空字符串。
- `FileService.transferToOpenAIFile` 已从 `FileDB` 读取 `metadata`、`cities`、`tags` 并映射到统一响应模型，因此不需要新增响应字段或数据库列；目录查询会沿用同一转换路径返回新增值。
- 现有 `FileControllerMkdirTest` 已覆盖成功、purpose、默认请求、非法名称/描述、重复目录和锁冲突等路径，可在该测试文件中扩展新增契约覆盖。

## 方案

1. 在 `FileSystemOps.MkdirOp` 增加可选的 `metadata`、`cities`、`tags` 字段，类型分别对齐文件上传接口的 `String`、`List<String>`、`List<String>`；不改变已有构造和 JSON 字段兼容性。
2. 在 `FileController.mkdir` 的现有基础参数校验之后复用 `validateCitiesJson` 与 `validateTagsJson`，让数组 JSON 总长度沿用文件创建接口的 512 字符限制，并在进入祖先校验、锁和 Service 前返回统一参数错误；`metadata` 按现有文件创建接口原样透传，不引入新的目录专属格式或校验规则。
3. 扩展 `FileService.mkdir` 参数，将请求中的 `metadata`、`cities`、`tags` 传入目录记录：`metadata` 未提供时继续落为当前 `{}` 默认值，`cities`/`tags` 未提供时继续落为空字符串，提供时使用现有 `JsonUtils.toJson` 序列化后写入 `FileDB`。保持目录节点类型、目的、广播和查询流程不变，使创建响应和后续查询自然复用统一转换结果。
4. 更新 mkdir Controller 测试中的 Service mock/verify 参数，并新增覆盖：带完整元数据的成功请求验证 Service 透传及响应字段；不带新增字段的请求验证兼容默认值；合法空/非空 `cities`、`tags` 的序列化契约；超过 512 字符的 `cities` 或 `tags` 验证明确 Bad Request 且不会获取锁或调用 Service。补充 Service 层测试或使用现有 Service 测试基础设施，验证 `FileDB` 的 `metaData`、`cities`、`tags` 写入、查询转换及创建响应返回，避免只验证 Controller mock 参数。

## 验证

- 运行 `cd api && mvn -Dtest=FileControllerMkdirTest test`，确认新增字段请求绑定、透传、默认值和非法参数均符合接口契约。
- 运行覆盖 `FileService.mkdir` 的定向 Service 测试（新增或并入现有 Service 测试类），断言 `fileRepo.addFile` 收到的目录记录中 `metaData` 保留输入、`cities`/`tags` 为 JSON 数组，缺省值仍为 `{}`/空字符串，并断言从查询记录构造的 `OpenAIFile` 返回列表字段。
- 运行 `cd api && mvn test`，回归文件上传、目录查询、元数据更新、移动及资源节点相关测试，确认新增 Service 参数没有破坏既有调用方。
- 通过 MockMvc 断言 HTTP 响应包含 `metadata`、`cities`、`tags`；通过查询接口或 Service 查询同一目录再次断言持久化后的值，覆盖“创建即返回”和“后续查询返回”两个验收路径。

## 非目标

- 不新增或修改数据库表结构、迁移脚本或统一 `OpenAIFile` 响应字段。
- 不改变已有目录元数据更新接口，不扩展 `POST /v1/files/resources`，不新增前端编辑界面。

## 风险与边界

- `cities`、`tags` 的存储格式必须继续使用现有 `JsonUtils.toJson`，避免目录数据与文件数据的反序列化行为不一致；空列表按现有 `CollectionUtils.isEmpty` 校验与默认存储约定处理。
- `metadata` 当前文件上传接口以字符串接收，方案保持相同语义，不在 mkdir 中擅自解析或限制 JSON，以免引入与既有公开契约不一致的兼容性变化。
- 任何校验失败都应发生在锁和持久化之前；已有名称、描述、purpose、祖先及重复目录校验顺序保持不变。
