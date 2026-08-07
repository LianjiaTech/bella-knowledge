针对当前 issue 产出可审阅的 Decision 或 Visual Plan，不改业务代码，并提交产物等待审阅。

要求：
- 必须先读取注入的 Issue Flow Skill 和 Vision Plan Skill。
- 能从仓库代码、文档、配置、测试、历史方案和 issue comments 中确认的信息，不要再问用户。
- 根据 `type::feature`、`type::debt` 或 `type::ops` 确定方案重点。
- 首先判断是否存在会实质改变方案的矛盾、范围歧义或用户选择。
- 存在阻塞选择时，只生成并提交 Decision artifact，然后停止。
- 没有阻塞选择，或 Decision 已批准时，生成完整 Visual Plan。
- 生成 Plan 时，在 commit 前删除同一 issue 目录下已完成使命的 `decision/` 目录。
- Decision 只呈现无法从 issue、仓库和现有约定中确定的真实阻塞选择，以及选项、推荐、判断标准和后果；每个可见区块都必须直接帮助用户作出选择。
- Decision 和 Plan JSON 是语义源；分别写入 `decision/data/decision.json.isv` 和 `plan/data/plan.json.isv`，`.isv` 是 Preview 发现契约，JSON 内的 `artifact` 字段继续区分 `decision` 与 `plan`。
- 内置组件的 HTML、CSS、JavaScript、布局、图形和评论锚点全部由 Issue Flow Engine 根据 JSON 渲染。
- 当 Vision Plan Skill 判断需要可交互 Demo 时，可以在 `plan.json.isv` 同目录额外生成自包含 HTML，并通过 `custom-html` section 的 `file` 字段引用；除此之外，不生成产物 HTML、CSS、JavaScript 或渲染资源。
- 使用运行时提供的工作分支，提交运行时指定的全部产物后，通过统一 CLI 的 `pr submit plan --artifact decision|plan` 完成发布。
- 不直接调用 `gh`、`glab` 或 provider API。

回复：
- Decision 成功：只说明 Decision 路径、Engine URL 和等待决策。
- Plan 成功：只说明 Plan 路径、Engine URL 和等待审批。
- 阻塞：说明缺少的配置、变量或权限。
