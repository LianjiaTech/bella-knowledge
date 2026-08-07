# Label 语义

## Managed Label Groups

所有 managed label 按 prefix 分组，同一 prefix 内互斥（apply.cjs 自动处理替换）。

### type::

Issue 类型。一个 issue 只有一个 type。

| Label | 语义 |
|-------|------|
| `type::feature` | 新功能或功能增强 |
| `type::bug` | 已有功能的缺陷或回归 |
| `type::debt` | 重构、可维护性、工程质量 |
| `type::ops` | 发布、环境、权限、CI/CD、生产运维 |
| `type::docs` | 纯文档新增、修订、迁移或信息架构调整 |
| `type::optimization` | 分析并改善已有任务的自动化完成度 |

公开类型枚举固定为 `feature|bug|debt|ops|docs|optimization`。`type::docs` 使用现有 `triage -> build -> approve` 路径，不新增 flow，也不要求 Plan；同时包含代码行为变更的工作仍按其主要产品或工程类型分类。

### status::

Issue 生命周期状态。

| Label | 语义 |
|-------|------|
| `status::active` | 活跃，应继续处理 |
| `status::done` | 已完成（终态） |
| `status::drop` | 不再处理（终态） |
| `status::suspend` | 暂停，不自动 resume |

终态（`done`/`drop`/`suspend`）不会被自动化流程 resume。

### flow::

当前所处的工作流阶段（下一步动作）。

「可自动化」列指该步是否可被自动化驱动（而非人工 gate）；标 `是` 的步骤是否真正自动触发，还要由 `automation::` 上限决定（见下）。

| Label | 语义 | 可自动化？ |
|-------|------|-----------|
| `flow::triage` | 等待分类/规范化 | 是 |
| `flow::plan` | 等待方案规划 | 是 |
| `flow::build` | 等待实现 | 是 |
| `flow::clarify` | 等待人工补充信息 | 否（gate） |
| `flow::approve` | 等待人工审批 | 否（gate） |

### automation::

Issue 级别的自动化策略：**允许自动化推进到的上限**。与 `flow::` 正交——`flow::` 是当前这一步，`automation::` 是这个 issue 能自动跑多远。

| Label | 语义 |
|-------|------|
| `automation::off` | 显式关闭该 issue 的 intake 默认补标与自动 triage/plan/build |
| `automation::plan` | 自动推进到 plan 为止，build 需人工触发 |
| `automation::build` | 自动推进到 build；Markdown Plan PR/MR 合并或 Visual Plan 审批合并后续推 |

自动化级别排序：`off` < `triage` < `plan` < `build`

有效级别 = issue 上的 `automation::` label；如果 issue 没有 `automation::` label，则使用 repo 默认级别。

重要：issue 上的 `automation::` label 会覆盖 repo 默认级别。不确定时不标，沿用 repo 默认。只想沉淀讨论结果且暂不自动化时，标 `automation::off`。

apply.cjs 和 create-issue.cjs 接受 `automation::off`、`automation::plan` 和 `automation::build`。
`automation::triage` 不是合法 label 值。

### optimization::

来源 Issue 的自动化优化处理状态。Turns 阈值只用于判断是否显示“分析优化”，不写入 label。

| Label | 语义 |
|-------|------|
| `optimization::analyzing` | 已创建关联优化 Issue，正在处理 |
| `optimization::analyzed` | 关联优化 Issue 已完成 |

### priority::

| Label | 语义 |
|-------|------|
| `priority::p0` | 紧急/严重阻塞 |
| `priority::p1` | 高影响 |
| `priority::p2` | 默认 |
| `priority::p3` | 低优先级 |

### size::

Issue 工作量规模。用于 Weighted Throughput 统计；同一 issue 必须最多一个 `size::`。

| Label | 权重 | 语义 |
|-------|------|------|
| `size::XS` | 0.5 | 文案、配置、单点小改 |
| `size::S` | 1 | 局部低风险改动 |
| `size::M` | 2 | 常规单 issue 工作；无法判断时的默认值 |
| `size::L` | 3 | 跨模块或高回归面工作 |
| `size::XL` | 5 | 大范围、架构性或应考虑拆分的工作 |

进入 `flow::plan` 或 `flow::build` 前，issue 必须有且仅有一个 `size::`。缺失时，agent 根据 issue 标题、正文、评论和仓库上下文补打；无法判断时用 `size::M` 并留下低置信度说明。多个 `size::` 会阻断 plan/build 流转，必须先修正为一个。

Weighted Throughput 的最小定义：在给定时间窗口内，对完成的 issue 按唯一 `size::` label 映射权重并求和。完成口径建议使用 `status::done`，或 build PR/MR merge 后由 `pr merged` 转为 done 的 source issue。没有 size 或有多个 size 的 issue 不应进入统计。

### feature:visual-plan:

Issue 级 Plan 模式开关。

| Label | 语义 |
|-------|------|
| `feature:visual-plan:on` | 使用 Decision/Visual Plan 页面、本地审阅记录和自动合并流程 |

未设置该标签时使用 Markdown Plan PR/MR 流程；通过 `issue apply --clear-visual-plan-feature` 移除 opt-in。

### mr-by::

PR/MR 来源标记。**只用于 PR/MR，不用于 issue。**

| Label | 语义 |
|-------|------|
| `mr-by::plan` | Markdown Plan PR/MR 来源标记 |
| `mr-by::build` | PR/MR 由 build action 创建 |

### review::

PR/MR 级自动 review 暂停开关。**只用于 PR/MR，不用于 issue。**

| Label | 语义 |
|-------|------|
| `review::off` | 暂停新的自动 review 和 review-comment task resume；移除后恢复仓库默认行为 |

`review::off` 不会取消已经运行的 Agentrix task，也不会补放暂停期间错过的事件。仓库级 `ISSUE_FLOW_REVIEW_ENABLED` 关闭时始终全局禁用；仓库级开启时，该标签可以对单个 PR/MR 施加更严格的暂停。使用 `labels sync` 或安装升级提供标签，使用 `labels check` 诊断缺失或 metadata 漂移。

## 互斥规则

- 同一 prefix 内只保留一个 label
- apply.cjs 在变更某个 prefix 时，自动移除该 prefix 下的旧 label
- 不指定某个 prefix 时，不触碰该 prefix 的现有 label
- `--clear-flow` 清除所有 `flow::` label 但不添加新的
- `--clear-automation` 清除所有 `automation::` label 但不添加新的
- `flow::plan` / `flow::build` 执行前必须有且仅有一个 `size::` label
