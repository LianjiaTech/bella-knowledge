针对当前 issue 产出可审阅的实现方案，并提交方案的 PR/MR，不改代码。

要求：
- 能从仓库代码、文档、配置、测试或历史方案中确认的信息，不要再问用户。
- 根据 issue 的 `type::feature`、`type::debt`、`type::ops` 或 `type::docs` 确定方案重点：
   - feature：目标体验、数据/接口/状态变化、交互路径、验收。
   - debt：边界、重构步骤、兼容性、迁移风险、回归面。
   - ops：环境、权限、CI/CD、发布或生产操作路径、失败回滚。
   - docs：目标读者、文档范围、信息架构、事实来源和内容时效性；验证方案必须覆盖链接可达性与锚点、示例可运行性、命令有效性，以及关键事实与当前代码、配置或权威资料的一致性，不能只检查格式。
- 若仍缺少仓库无法推断的关键事实，使用统一 CLI 的 `issue apply` 将 issue 转到 `flow::clarify`，然后直接提问。
- 按运行时提供的 Plan template 结构，把方案写入 Plan output file。
- 提交前按运行时提供的仓库上下文创建或切换到工作分支，不要直接在基准分支提交。
- 提交方案文件，写 PR body，然后使用统一 CLI 提交 plan PR/MR。

澄清提问：
- 直接提问，不解释标签变化。
- 问题要体现你已经读过仓库：给出自己的技术建议、推荐默认选项，并尽量用选择题降低决策成本。

PR 要求：
- title 必须关联 issue 号，推荐 `Plan #<number>: <short title>`。
- PR body 必须包含 Source issue、Plan file、Summary、Review focus。
- PR body 写入仓库外临时文件（例如 `mktemp`），通过 `issue-flow pr submit ... --body-file` 提交，不要加入 git。

回复：
- 成功：只说明方案文件路径、PR/MR URL、issue 已进入审批。
- 需要澄清：直接提出问题；优先给选项和你的推荐。
- 凭证阻塞：说明阻塞点和需要用户采取的行动。
