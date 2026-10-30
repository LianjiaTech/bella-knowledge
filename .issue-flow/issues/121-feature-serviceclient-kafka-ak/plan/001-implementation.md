## 目标

在不改变普通 API Key 协议、文件 API 和 Kafka topic 的前提下：

- 让 `ServiceClient` 请求以调用系统身份完成认证，并以 `X-Effective-Api-Key-Code` 指定的真实用户 AK 作为后续权限、路由、额度、计费和文件归属身份。
- 让新写入的文件、上传记录和目录/资源记录保存真实用户 AK，不再把调用系统 AK 写入文件归属字段。
- 让文件及目录生命周期 Kafka 事件的 `ak_code` 稳定表示原始用户文件 AK；对 dom-tree/pdf 派生文件和历史空 AK 定义可验证的兼容回退。
- 保持普通 API Key 行为、现有上传 API 调用方式和 Kafka JSON 字段名 `ak_code` 兼容。

## 方案

### 1. 鉴权与有效业务身份

升级 `api/pom.xml` 中的 `top.bella:openapi-spi` 至 `1.2.78`，使用该版本提供的 ServiceClient 识别和上下文能力；不在 file-api 自行复制签名算法或另造请求协议。

请求入口仍由 `RequestInterceptor` 统一处理：

- 普通请求保持当前 `Authorization: Bearer <api-key>` 流程，调用现有普通 API Key 校验并将返回的 `ApikeyInfo` 放入 `BellaContext`。
- ServiceClient 请求要求同时具备并分别解释以下三个头：
  - `Authorization: Bearer <service-client-credential>`：调用方凭证，用于证明调用系统身份。
  - `X-Service-Client`：调用系统编码，用于标识调用系统。
  - `X-Effective-Api-Key-Code`：真实用户 AK Code，用于确定实际业务身份。
- ServiceClient 的调用方凭证、调用系统编码及其绑定关系复用 SPI/上游 ServiceClient 校验入口；缺失、格式错误或校验失败均在拦截器阶段返回 HTTP 401，不进入 Controller、文件服务或数据库写入。
- ServiceClient 通过 `apikeyService.verifyAuthByCode(effectiveAkCode)` 校验有效用户 AK。该调用负责按 Code 查询有效 AK、检查状态和父 AK；返回的 `ApikeyInfo` 是唯一的后续业务身份来源。返回为空、无效或校验异常统一按 HTTP 401 处理。
- 校验成功后把有效用户 `ApikeyInfo` 写入 `BellaContext.setApikey`，并用其中的用户标识/名称建立 `Operator`；`BellaContextHelper.getOperatorAkCode()` 因此返回有效用户 AK Code，而不是 ServiceClient 或系统 AK。调用系统信息只用于鉴权审计，不参与文件归属、权限和 Kafka `ak_code`。
- ServiceClient 请求不允许仅因 `Authorization` 是合法系统凭证就回退到系统 AK；三头必须完整，且 effective AK 必须经过 `verifyAuthByCode`。

这样既保留普通请求现有语义，也让下游已经依赖 `BellaContext.getApikey()`、`BellaContextHelper` 和 `Operator` 的权限/空间逻辑自然看到真实用户身份。

### 2. 文件落库与上传链路

以“请求进入文件业务前解析出的有效用户 AK”为入口事实，以显式参数在跨方法、跨异步阶段传递，避免在深层方法重新读取可能变化或已不存在的请求上下文。

- `FileService` 的直接上传、stream/multipart、导入和 `createFileWithId` 路径统一接收/传递 `akCode`，创建 `FileDB` 时写入该值。
- 保留现有对外上传 API 的参数和调用兼容性；如需调整内部方法签名，保留兼容重载，仅由新入口负责捕获有效 AK，内部链路不再以 `BellaContextHelper.getOperatorAkCode()` 覆盖已传入值。
- `UploadService.create` 将有效 AK 写入 `FileUploadDB.akCode`；上传完成、重试或异步 finalize 使用上传记录中的 AK，不能在完成阶段重新从当前请求上下文读取调用系统 AK。
- 目录创建和资源节点创建在落库时使用当前有效用户 AK；更新、移动、删除等操作不改写已有文件记录的 `ak_code`，除非现有业务明确执行文件归属变更（本需求不新增该语义）。
- dom-tree/pdf 等派生文件保存其自身记录时保留创建链路传入的 AK，广播时仍按“源文件优先”规则确定事件 AK，避免解析器/系统文件的运行身份污染原始文件归属。

覆盖的入口以当前调用链为准，包括 `FileService` 的普通上传、导入、异步完成、目录和资源节点，以及 `UploadService` 的创建/完成/重试路径；不通过全量数据回填解决历史问题。

### 3. 广播 `ak_code` 归属规则

集中收口 `FileService.broadcast` 前的 AK 解析，所有创建、更新、删除、目录、导入和异步事件都通过同一规则设置 `FileBroadcasting.akCode`，不再允许各构造点直接用请求上下文覆盖消息。

按以下优先级解析，优先级从高到低：

1. **派生文件的源文件记录 AK**：文件 purpose 为 dom-tree/pdf 时，优先使用显式源文件查询结果；没有显式源文件时使用 `FileRepo.queryFileByDomTreeFileId` 或 `queryFileByPdfFileId` 查询原始用户文件。源文件 `ak_code` 非空即为事件 `ak_code`。
2. **当前事件文件记录 AK**：普通文件、目录、资源和上传完成事件使用事件对应 `FileDB.akCode`；派生文件在源文件缺失/查询失败/源文件 AK 为空时，如果自身记录已有非空 AK，也使用自身记录 AK。
3. **当前同步请求的有效业务 AK**：仅当历史记录的文件 AK 为空，且事件在当前同步请求内产生时，才使用 `BellaContext` 中已经通过校验的有效用户 AK 作为兼容回退。该回退只补齐消息，不回写历史数据库记录，也不覆盖任何已有非空文件或源文件 AK。
4. **无上下文的异步/重试场景**：若历史文件及其源文件均为空，且没有可传递的上传记录 AK，则保持 `ak_code` 为空并记录可检索的告警；不使用系统 AK 猜测归属，不因为历史数据触发全量回填，也不因源文件查询失败覆盖已有非空 AK。

源文件查询失败不应回滚已经成功的文件操作；广播继续按上述后续优先级发送。若有效用户 AK 校验失败，则在业务事务前直接 401，不能执行文件操作。这样区分了“新请求身份校验失败”和“历史数据归属未知”两类问题。

`FileBroadcasting.akCode` 保持现有字段，Kafka JSON 序列化继续由 `KafkaConfig` 输出 `ak_code`；不新增 topic、不改变消息整体协议，也不将调用系统编码放入 `ak_code`。

### 4. 对待定决策的结论

1. **三个请求头及校验入口**：采用上面定义的 `Authorization`、`X-Service-Client`、`X-Effective-Api-Key-Code` 三头组合；调用方凭证和 ServiceClient 编码绑定复用 SPI/上游 ServiceClient 校验，effective AK 统一调用 `apikeyService.verifyAuthByCode`。理由是 `1.2.78` 已提供 ServiceClient 识别及上下文常量，而有效用户 AK 的状态/父 AK 语义由指定服务端 API 承担，file-api 不复制认证实现。
2. **有效用户 AK 的上下文字段**：写入 `BellaContext` 的 API Key/`ApikeyInfo.code`，同时用返回信息建立 `Operator`；不把系统 AK 写到 `managerAk` 作为替代身份。理由是现有 `BellaContextHelper.getOperatorAkCode()` 优先读取 API Key code，能在普通请求和代用户请求间保持同一调用语义，并使下游权限、路由、额度和计费读取同一个有效身份。
3. **派生文件和异步链路**：事件阶段以源文件记录为最终归属来源，落库/上传阶段以显式 `akCode` 参数和 `FileUploadDB.akCode` 传递；源文件查询通过现有 dom-tree/pdf 查询能力完成。理由是派生文件本身可能由系统创建，不能把解析器身份当作用户文件归属；显式传递又能覆盖异步完成时没有原始 HTTP 上下文的场景。
4. **历史空 AK 回退边界**：只对当前同步广播使用已经验证的有效用户 AK；异步重试不凭空使用线程上下文，优先使用上传记录/文件记录，仍为空则发送空值并告警。理由是请求上下文不具备跨异步生命周期的可靠性，且用系统 AK 填补未知历史归属会制造错误数据。
5. **错误码、日志和事务**：三头缺失/格式错误、ServiceClient 校验失败、`verifyAuthByCode` 返回无效或抛出鉴权异常均统一 HTTP 401，日志只记录请求标识、调用系统和失败原因类别，不记录凭证；文件操作在拦截器失败时不开始。源文件查询失败记录 warning 并按广播回退规则继续，不改变已完成文件事务；普通 API Key 的既有错误和回归范围保持不变。

### 5. 验证方案

实现后按以下层次验收：

- **依赖与构建**：确认 `openapi-spi` 为 `1.2.78`，编译通过；验证新增 SPI 类型/方法在构建依赖中可用。
- **鉴权单测**：覆盖普通 API Key 成功与失败；ServiceClient 三头齐全成功；缺少任一头、非 Bearer/空值、调用方凭证或编码绑定校验失败、effective AK 不存在/失效/父 AK 无效均返回 401，并验证服务方法未被调用。
- **上下文单测**：断言成功的 ServiceClient 请求中 `BellaContext` 的 `ApikeyInfo.code`、Operator 用户信息和 `BellaContextHelper.getOperatorAkCode()` 都对应 effective AK，而不是 ServiceClient/system AK；普通请求断言既有结果不变。
- **落库与上传单测**：覆盖直接上传、stream/multipart、导入、目录、资源、上传记录创建、上传完成和重试；断言 `FileDB`/`FileUploadDB.akCode` 来自有效用户 AK，异步完成不重新读取系统上下文，并验证既有上传 API 调用兼容性。
- **广播单测**：覆盖文件创建/更新/删除、目录、导入、异步完成；分别验证普通文件记录 AK、派生文件源文件 AK、源文件缺失/查询异常、历史空 AK 的同步回退，以及已有非空 AK 不被请求上下文覆盖。
- **Kafka 序列化测试**：验证消息 JSON 仍包含字段名 `ak_code`，值遵循上述解析优先级；不出现仅为实现内部传递而新增的协议字段。
- **必要集成验证**：使用真实/测试版 `apikeyService.verifyAuthByCode` 和 ServiceClient 请求头走一次文件创建、更新、删除、目录及上传/异步流程，确认 HTTP 401 边界、数据库记录和 Kafka 消息端到端一致。

## 非目标

- 不改变普通 API Key 的认证协议、权限模型、空间模型或对外文件 API 语义。
- 不改变 Kafka topic、事件名、消息整体协议或无关文件元数据字段。
- 不进行历史文件 `ak_code` 的全量回填，不以系统 AK 推测历史文件归属。
- 不把 ServiceClient 调用系统身份当作真实用户身份参与文件归属、权限、额度、计费或 `ak_code`。

## 风险与边界

- `apikeyService.verifyAuthByCode` 是 ServiceClient effective AK 的唯一权威校验；若其返回对象缺少用户信息，方案需要在实现前补齐映射，而不能回退到调用系统身份。
- 异步链路必须携带上传/创建时捕获的 AK；任何仍直接从 `BellaContextHelper` 读取 AK 的深层入口都属于实现遗漏，应在代码审查和测试中逐一清理。
- 历史空 AK 的事件可能仍为空，属于明确的兼容结果；审阅重点是不能误填系统 AK，且同步请求有确定的有效 AK 时能按规则回退。
- 源文件查询只决定派生事件归属，不改变派生文件本身的数据库记录和文件内容；查询异常采用告警加回退，避免影响主文件操作成功率。
