给当前 issue 进行分类打标签，判断是否可执行，选择下一步 flow，并在需要时规范化 issue 正文。

## 关键行为

1. **主动调查** — 在判定"信息不足"之前，先从仓库代码、配置、文档、commit 历史中查证。如果 issue 是一个疑问，尝试从仓库中找到答案。
2. **能补则补** — 能从仓库上下文确认的事实直接填入分类，不要标 `flow::clarify`。
3. **必须提问时** — 确实无法从仓库推断的缺失信息，标 `flow::clarify` 并生成提问内容：
   - 直接说明缺了什么、为什么需要
   - 给出选择项或推荐默认方案
   - 附上你的判断依据（查了什么、排除了什么）
   - 分类任务是异步执行，禁止调用 `askUser` 或任何需要等待用户回答的交互工具
   - 将问题写入最终回复，由 Issue Flow 流水线发布到 issue；提交分类结果后结束当前任务，不等待用户回答

## 必打标签

一次 `issue apply` 同时应用以下判断结果（语义与取值见 skill 的 label 体系）：

- `type::` — 需求类型。
- `priority::` — 处理优先级；无明显信号时用 `priority::p2`。
- `size::` — 工作量规模。进入 `flow::plan` 或 `flow::build` 前必须有且仅有一个 `size::`；无法判断时用 `size::M` 并留下低置信度说明。
- `flow::` 与 `automation::` — 判定方法见下两节。

`type::docs` 只用于纯文档新增、修订、迁移和信息架构调整；只要包含产品或代码行为变更，就按主要的 feature、bug、debt 或 ops 分类。公开类型枚举是 `feature|bug|debt|ops|docs|optimization`。

## 规范化正文

确定 `type::` 后，按对应模版 `.issue-flow/templates/type-<type>.md` 重写 issue 正文：

- 模版只定义必须具备的最小结构，不是允许保留内容的白名单。先盘点原文信息，再将其融合进最合适的模版章节；无法自然归入时，新增语义明确的章节。
- 不得因模版没有对应字段而删除原文中的背景、约束、证据、复现信息、链接、日志、示例、讨论结论或其他有价值内容。可以去重和改写；只删除明确重复、经查证错误或与 issue 无关的内容，不确定时保留。
- 模版字段优先取自 issue 本身，其次取自仓库代码、文档、配置和已有约定。
- 改写后的正文写到 repo 外临时文件（如 `mktemp`），随标签一起通过 `issue apply` 的 `--normalized-body-file` 应用；不要把正文文件提交到 git。
- 标 `flow::clarify` 时不需要提供规范化正文（会被忽略），把缺口写进提问即可。
- `type::docs` 的正文只需明确文档目标和目标读者；只有这两项无法从 issue 或仓库补齐时才进入 `flow::clarify`。

## 选择下一步 flow

可执行的 issue 要在 `flow::plan` 和 `flow::build` 之间做判定：

- `type::docs` 固定选择 `flow::build`，沿用 `triage -> build -> approve`，不创建 Plan。
- 选 `flow::build`：triage 调查后实现路径已确定（改哪些文件、怎么改、验收点都清楚），单一方案无需取舍，改动局部、不涉及架构或公共接口决策。
- 选 `flow::plan`：存在多个可行方案需先定方向；跨模块 / 架构 / 公共接口改动；改动面大需拆解；或调查之后实现路径仍不确定。

自检：如果你在规范化正文里已经写出了"怎么改"而不只是"要什么"，plan 的产物实质已经完成，应直接 `flow::build`。

## 判断自动化级别

`automation::` 与 `flow::` 正交，独立判断：它是**允许自动化推进到的上限**，决定这个 issue 能自动跑多远，而不是当前这一步。

- `automation::plan`：自动推进到 plan 为止；build 需人工触发。
- `automation::build`：自动推进到 build；若走 plan，人工合并 plan PR 后自动续推。

issue 上的 `automation::` label 会覆盖 repo 默认级别；不确定就不标，沿用 repo 默认。按需求的风险、影响面和你对实现路径的把握来定，例如：

- 路径清楚、低风险、希望自动实现：标 `flow::build` + `automation::build`。
- 需要先规划，但 plan 通过后可自动实现：标 `flow::plan` + `automation::build`。
- 路径清楚但想在动代码前留一道人工确认：标 `flow::build` + `automation::plan`，build 会停在 gate 等人工触发。
