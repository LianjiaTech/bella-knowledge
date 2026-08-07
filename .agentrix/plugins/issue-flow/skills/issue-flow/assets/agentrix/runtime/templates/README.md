# Issue Flow 模版

Triage 识别 issue 类型后使用这些模版。模版同时承担两件事：

- 作为 issue 正文的目标格式；
- 作为信息是否完整的检查清单。

标签和模版的对应关系：

- `type::bug` -> `type-bug.md`
- `type::feature` -> `type-feature.md`
- `type::debt` -> `type-debt.md`
- `type::ops` -> `type-ops.md`
- `type::docs` -> `type-docs.md`
- `type::optimization` -> `type-optimization.md`

模版字段可以来自 issue，也可以来自仓库代码、文档、配置或已有约定。能从仓库确认的信息不要再问用户。

如果无法从 issue 和仓库上下文中补齐必要字段，triage 必须设置 `flow::clarify`。面向人的回复应直接提问，优先给选择项、推荐默认方案和判断依据。

如果必要字段已经足够，triage 应把 issue 正文改写成对应模版格式，并通过项目级 Claude skill `issue-flow` 的 `${CLAUDE_SKILL_DIR}/cli.cjs issue apply --normalized-body-file <path>` 应用标签和正文。

Plan 使用单独的方案模版：

- `type::bug` -> `plan-bug.md`
- `type::feature` / `type::debt` / `type::ops` / `type::docs` -> `plan-impl.md`
- `type::optimization` -> Optimization JSON contract from the Automation Optimizer Skill

`type::docs` 的常规路径是 `triage -> build -> approve`，不要求 Plan。`plan-impl.md` 仅用于显式启动的例外 plan action。
