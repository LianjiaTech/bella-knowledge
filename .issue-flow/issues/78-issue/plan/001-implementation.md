## 目标

在不改变现有小文件 `POST /v1/files` 单请求接口及文件解析、列表、预览、管理链路的前提下，为 Web 大文件上传增加可恢复的分片会话流程：上传入口按统一阈值选择单请求或分片；分片经应用服务鉴权并写入统一存储抽象；全部分片完成后原子地合并为一个对象，再复用现有 `FileService` 文件记录创建、广播和解析流程。未完成、取消、失败或超时的会话不得暴露为可用文件记录，也不得长期留下无主临时对象。

## 当前上下文

- Web 端 `web/src/request/files.ts` 的 `postUploadFile` 将文件整体放入 `FormData`，`web/src/components/upload-dialog.tsx` 目前只展示上传中/解析中两种状态。
- API 的 `api/src/main/java/com/ke/bella/files/api/FileController.java` 通过 Spring `MultipartFile` 接收完整文件；`api/src/main/resources/application.yml` 与 Docker 配置的 `spring.servlet.multipart.max-file-size`、`max-request-size` 均为 `512MB`。
- Nginx 模板存在 `client_max_body_size 100M` 与 `1024M` 两种部署配置；分片请求应小于单请求限制，不能把放大 multipart 上限作为方案核心。
- `FileService` 已将创建数据库记录、存储写入、`finalizeFileUpload` 广播/解析链路分开；分片阶段应只创建上传会话，不调用 `createFile` 或 `finalizeFileUpload`。
- `StorageService` 目前只有整文件和流式整文件写入；`S3StorageService` 使用 AWS SDK v2，`LocalStorageService` 使用本地目录，均需扩展分片会话能力。
- 仓库使用 jOOQ 生成数据库访问代码，`api/sql` 通过增量 SQL 管理表结构；已有 `file_temp`、`file_progress_temp` 等临时数据表，但没有现成的上传会话/分片模型。

## 方案

### 1. 增加上传会话模型与数据库状态

新增独立的上传会话表和分片状态表，避免把未完成对象写入 `file_*` 文件分表。建议字段如下，具体命名按现有 SQL/jOOQ 风格落地：

- 会话：`upload_id`、空间/操作者标识、文件名、文件总大小、总分片数、分片大小、内容类型/扩展名/字符集、purpose/metadata/ancestor/overwrite/description/cities/tags、客户端文件指纹、状态、已完成字节数、存储类型、存储会话标识、过期时间、创建/更新时间，以及完成后的 `file_id`。
- 分片：`upload_id`、分片序号、声明大小、实际大小、内容校验值、存储侧 part/object 标识、状态、创建/更新时间；对 `upload_id + part_number` 建唯一约束。
- 状态至少区分 `INITIATED`、`UPLOADING`、`COMPLETING`、`COMPLETED`、`CANCELLED`、`FAILED`、`EXPIRED`；完成、取消、过期等终态禁止再次写入。
- 客户端文件指纹用于断点续传查询，指纹至少由文件名、大小、最后修改时间和客户端计算的内容摘要组成；服务端仍以会话归属和显式参数校验为准，不能仅凭指纹授权。
- 增加增量 SQL，并重新生成 jOOQ 表/记录类；迁移必须保持现有 `file_*` 分表和旧接口数据不变。

会话初始化时在事务内校验空间、父目录、文件名、purpose、元数据和大小，记录当前操作者归属；同一操作者对同一指纹存在未过期会话时返回该会话及已完成分片清单，避免重复创建存储会话。初始化不创建 `FileDB`，也不广播文件创建事件。

### 2. 以应用服务为统一分片入口

采用“客户端 -> 应用 API -> 存储”的方案，而不是本次引入预签名直传：

- 统一应用 API 便于复用现有 `BellaContextHelper` 空间/操作者鉴权、父目录校验、覆盖语义和异常协议。
- 本地存储无法使用 S3 预签名 URL；由应用服务承载可使 S3/MinIO 与本地模式保持同一客户端契约和校验逻辑。
- 每个分片请求只携带一个小的 `MultipartFile`/请求体，网关与 Spring multipart 限制只需覆盖分片大小；部署模板同步提供明确的分片请求大小、超时和请求缓冲配置。
- 该选择会增加 API 流量和服务端带宽，但不改变现有部署拓扑；后续若需要降低 API 流量，可在不改变会话/完成协议的情况下增加存储层直传适配。

新增 `FileController` 分片接口，建议保持 REST 语义并使用会话 ID：

1. `POST /v1/files/uploads`：初始化，提交文件元数据、总大小、分片大小、总分片数、指纹及现有上传参数；返回 `upload_id`、协商后的分片参数、已完成分片列表、过期时间。
2. `PUT /v1/files/uploads/{upload_id}/parts/{part_number}`：上传分片；校验会话归属/状态、序号范围、除最后一片外的大小、最后一片大小、请求实际大小和可选校验值；重复上传同一分片时校验内容一致并返回原结果，不重复创建 part/object。
3. `POST /v1/files/uploads/{upload_id}/complete`：要求所有序号均已成功，按序提交存储合并；成功后只创建一个 `FileDB` 并调用现有 `finalizeFileUpload`，返回现有 `OpenAIFile`。
4. `DELETE /v1/files/uploads/{upload_id}`：幂等取消并触发存储 abort/临时分片删除；已完成会话不删除已入库文件，终态重复取消返回稳定结果。
5. `GET /v1/files/uploads/{upload_id}`：返回会话状态和已完成分片列表，供断点续传恢复；不得返回半成品文件记录。

接口应沿用现有错误处理和请求上下文；complete 过程需要在会话状态上加并发控制，确保重复 complete 只返回已记录的完成结果，不重复合并、建档、广播或解析。

### 3. 扩展统一存储抽象

在 `StorageService` 增加会话化能力，抽象出初始化、写入/查询分片、完成合并、取消清理和临时对象回收等操作，返回与后端无关的 `StorageUploadSession`、`StoragePart`、`CompletedObject` 结果。服务层只保存存储会话 ID、分片标识和校验值，不依赖 AWS SDK 类型。

- `S3StorageService` 使用 S3/MinIO multipart upload：初始化创建 multipart upload；分片上传映射为 `UploadPart`；用 part number + ETag 以稳定顺序完成；重复 part 先查询/比对会话记录，避免重复上传；取消调用 abort。应处理 complete/abort 的重试和已完成对象的幂等识别。
- `LocalStorageService` 在 `bella.file-api.file.tmp-file-dir` 下按不可猜测的 upload ID 建立临时目录，每个 part 写入独立临时文件并通过原子替换完成；complete 按序流式合并到唯一临时目标，校验总字节数后原子移动到最终 key，再写入现有本地元数据；取消/过期删除临时目录。
- 两种实现都不得在分片阶段写入最终文件 key；complete 失败时保留可重试的会话/分片状态或转失败并清理，不能生成半成品可见对象。
- 存储层清理必须可重复执行，并以会话状态/数据库记录为边界，避免误删同 key 的已完成文件。

### 4. 接入现有文件入库和兼容路径

新增 `MultipartUploadService`（或同等职责的服务）编排会话、存储和 `FileService`：

- 初始化复用 `FileController`/`FileService` 当前的元数据、空间、目录和文件名校验；在完成前再次获取文件名锁，重新检查同名文件，覆盖模式沿用现有更新语义，避免会话期间目录状态变化造成覆盖竞态。
- complete 成功后以存储完成对象的输入流/临时文件调用现有 `FileService` 创建文件记录和 `finalizeFileUpload`；若现有 `FileService` 需要避免再次复制大对象，则扩展一个“已完成存储对象入库”的内部入口，但保持解析、广播、列表、预览和管理侧契约不变。
- 文件记录创建与会话状态变更采用可恢复的状态机：只有 `FileDB` 创建成功且最终对象存在时才标记 `COMPLETED`；完成响应返回已持久化的 `file_id`。若数据库写入失败，清理对象并允许在明确状态下重试，避免重复 file record。
- 现有 `POST /v1/files` 不改协议、不改调用方行为；Web 仅在文件大小达到配置阈值时切换新流程，小文件继续调用 `postUploadFile`。

### 5. Web 分片、重试和进度

在 `web/src/request/files.ts` 增加初始化、状态查询、分片上传、完成和取消请求，保持现有 API client/错误处理风格。在 `web/src/components/upload-dialog.tsx`：

- 以配置化阈值选择流程；默认阈值应低于最小部署单请求上限，并与服务端能力配置保持一致，不能把 `512MB` 作为浏览器端硬编码上限。
- 按协商分片大小切分 `File`，限制并发数，逐片记录成功状态；失败只重试当前分片，使用有限次数、指数退避和可取消的 AbortController，成功分片不重传。
- 总进度按已确认分片字节数加当前分片上传进度计算，显示百分比/已上传大小；完成合并单独显示，解析阶段继续复用现有轮询。
- 初始化或页面重试时优先查询会话已完成分片并跳过；取消、网络错误和弹窗关闭按明确策略调用取消接口或保留会话供恢复，不能静默遗留会话。
- complete 返回的唯一文件对象走现有 `onAddUploadFile`、引用添加、选中和解析成功提示路径。

### 6. 超时清理、配置和运维

- 增加会话 TTL、清理批次/间隔、最大分片大小、最大总文件大小、并发和重试次数等配置；服务端对这些值做上限校验，前端只使用服务端协商结果。
- 增加定时清理任务或复用现有任务执行机制：扫描过期且非完成会话，先标记/抢占清理状态，再调用存储 abort/delete，最后删除分片和会话记录；操作幂等并记录失败重试日志/指标。
- 同步 `application*.yml`、Docker/Nginx 模板和部署说明，确保分片请求体、上传/读取超时、`proxy_request_buffering` 等配置一致；旧单请求接口继续受原限制保护。
- 增加监控维度：初始化、分片失败/重试、完成耗时、取消/过期、清理失败及存储模式；日志禁止记录凭证和原始文件内容。

## 风险与边界

- 应用中转分片会增加 API 出入口带宽和磁盘/连接压力；通过分片大小、并发上限、请求超时和指标控制，不在本次改变部署拓扑或引入预签名权限模型。
- S3 multipart 与本地合并语义不同，统一抽象必须保证“分片阶段无最终文件、按序完成、可取消清理”；应覆盖 MinIO 兼容性和本地磁盘空间不足的失败路径。
- complete 与清理可能并发，必须以数据库状态条件更新/锁和存储幂等操作防止清理正在完成的上传；进程崩溃后由 TTL 清理恢复。
- 文件名/目录同名锁只在完成阶段重新获取，分片会话不预留可见文件名；因此需在验收中覆盖会话期间同名文件创建和 overwrite=true/false。
- 大文件解析仍受现有解析器和异步任务能力限制；本需求只保证上传传输与文件入库链路，不扩大格式或解析大小范围。

## 迁移

- 发布增量 SQL、jOOQ 生成代码和后端配置后先灰度启用服务端接口，默认保留小文件旧路径。
- Web 先以受控阈值启用分片；出现异常可关闭前端阈值开关，继续使用旧小文件接口，不影响已完成文件。
- 已存在未完成会话按 TTL 清理；迁移/回滚不删除既有 `file_*` 文件记录。部署升级时确认临时目录权限、可用空间、S3 multipart 权限（create/upload/complete/abort）和清理任务运行状态。

## 验证

1. **数据库与代码生成**：执行全量初始化及增量 SQL，验证上传会话/分片唯一约束、状态索引、TTL 查询和 jOOQ 生成/编译；确认旧 `file_*` 表及历史记录不变。
2. **接口校验**：覆盖初始化参数、空间/操作者归属、父目录、文件名、大小、分片大小、序号、最后分片规则、校验值、过期/终态会话；断言非法请求不触发存储写入。
3. **幂等与并发**：重复提交相同分片、相同分片不同内容、重复查询、重复 complete、complete 与 cancel/清理并发；断言最多一个存储对象、一个 `FileDB`、一次创建广播/解析触发。
4. **存储适配**：使用 mock/fake 验证 `S3StorageService` 的 create/upload/list/complete/abort 映射和 ETag 顺序；使用临时目录验证 `LocalStorageService` 分片写入、按序合并、原子落盘、元数据、取消和磁盘/IO 失败清理；两种模式返回一致的服务层状态。
5. **文件链路回归**：完成后断言沿用现有文件记录、列表、预览、下载、解析和后续管理；未完成会话在所有文件查询中不可见；小文件 `POST /v1/files` 原有行为和 overwrite 语义保持不变。
6. **清理与恢复**：模拟超时、进程重启、存储 abort 失败、数据库写入失败和重复清理，验证 TTL 任务可重试、无主对象最终回收且不误删已完成对象；检查指标和日志字段。
7. **Web 行为**：组件/请求层测试覆盖阈值路由、分片切片边界、并发上限、已完成分片跳过、单片有限重试/退避、取消、断网恢复、整体进度计算和 complete 后原解析轮询回调；执行 `npm run lint:check`、`npm run type-check`、`npm run build`。
8. **后端回归与部署验证**：执行分片相关定向测试及 `cd api && mvn test`；在本地存储和 S3/MinIO 配置各跑一条完整上传/取消/过期流程，并检查两套 Nginx 模板渲染后的分片请求大小、超时和缓冲配置。
