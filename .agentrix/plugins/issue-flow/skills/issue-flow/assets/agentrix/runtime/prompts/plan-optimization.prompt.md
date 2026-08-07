分析当前优化 Issue 对应的自动化缺口，生成结构化 Optimization Plan JSON，不改业务代码。

要求：
- 必须先读取注入的 Issue Flow Skill、Automation Optimizer Skill，以及 Automation Optimizer Skill 引用的产物协议。
- 根据完整 TaskEvent 找到 Agent 首次未完成的真实原因；不要把人工纠正直接改写成检查清单。
- 产物只包含目标与改进方案：当前问题用一句话概括；出现原因写 1–3 条短句，不得包含 Task ID、sequence、事件编号或消息读取过程。
- 每个可执行方案必须能够由独立 Issue 落地；不要合并必须由不同 Issue 落地的改动。
- 为每个 `project-change` 和 `issue-flow-feedback` 生成 Issue 标题、正文、类型、优先级、规模、执行阶段和额外标签。文档方案使用 `type::docs` 与 `flow::build`。
- Agent 可在当前仓库实施的改动使用 `kind: project-change`。
- 必须由当前项目开发者补齐构建环境、工具链、凭据、组织基础设施或运行配置，且 Agent 无法在仓库内安全实施时，使用 `kind: project-developer-feedback`；只说明项目开发者要做什么及验证方式，不生成 `issue`。
- Issue Flow 上下文传递，或 `.issue-flow` 体系内的流程、Skill、模板、脚本等公共能力存在缺陷时，使用 `kind: issue-flow-feedback`、`type::bug` 与 `flow::triage`。
- 项目级执行规范统一写入 `.issue-flow/instructions.md`；除该文件外，不得生成修改 `.issue-flow/` 下任何文件的方案。
- 使用专用 Optimization JSON 产物，不修改业务代码。
- 将 JSON 写入运行时指定路径，提交该文件，再使用运行时提供的命令发布 Optimization Plan。

回复：
- 成功：只说明 Optimization Plan 路径和审阅地址。
- 阻塞：说明缺少的证据、配置、变量或权限。
