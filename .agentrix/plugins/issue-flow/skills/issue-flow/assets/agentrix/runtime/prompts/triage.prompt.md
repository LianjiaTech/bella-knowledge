给当前 issue 分类、判断能否执行、选择下一步 flow，并把正文整理成后续阶段的输入：plan 和 build 只读 issue 正文，review 按其中的验收标准检查实现。

## 先调查

判定信息不足前，先从仓库代码、配置、文档和 commit 历史查证；issue 是疑问时尝试直接从仓库找到答案。能从仓库确认的事实直接写进正文，不要提问。

## 需要澄清时

只有缺少仓库无法推断、且会改变做法的信息时，才标 `flow::clarify`（不提供正文）。把问题写进最终回复，由流水线发布到 issue：说明缺什么、为什么需要、你查过什么，给出选项和推荐。任务异步执行，不调用 `askUser` 等交互工具，提交后直接结束。

## 分类

- `type::`：feature | bug | debt | ops | docs。docs 仅限纯文档改动，涉及产品或代码行为时按主要类型分类；`type::optimization` 由系统创建，不要选择。
- `priority::`：无明显信号时用 `priority::p2`。
- `size::`：恰好一个；无法判断时用 `size::M`，并在回复中说明置信度低。
- `automation::`：与 flow 正交，表示自动推进的上限——`automation::plan` 自动到 plan 为止、build 等人工触发；`automation::build` 允许自动推进到 build。默认不标，沿用仓库默认；按风险和实现路径把握判断：`flow::build` + `automation::build`（路径清楚、低风险、全自动）；`flow::plan` + `automation::build`（需先规划，plan 通过后自动实现）；`flow::build` + `automation::plan`（路径清楚但想在 build 前留人工确认）。

## 选择 flow

- `flow::build`：实现路径已确定（改哪里、怎么改、如何验收都清楚），单一方案、改动局部。`type::docs` 固定走 build。
- `flow::plan`：有需要取舍的多个方案、跨模块/架构/公共接口改动、需要拆解，或调查后路径仍不确定。
- 自检：正文里已经能写出"怎么改"，就选 build。

## 整理正文

按 `.issue-flow/templates/type-<type>.md` 重写：

- 模版是最小结构，不是白名单。原文的背景、约束、证据、复现、链接、日志和讨论结论都要保留，融合进合适章节或新增章节；只删除重复、经查证错误或无关的内容。
- 验收标准要能逐条检查。
- 选 `flow::plan` 时，补充「调查依据」（已确认的相关代码、配置和现状）和「待定决策」（plan 必须回答、且会改变方案的问题）。
- 选 `flow::build` 时，把已确定的改动位置和做法写进正文。
- docs 只需明确文档目标和目标读者，这两项补不齐时才澄清。

最后用一次 `issue apply` 同时应用标签和 `--normalized-body-file`；正文写到仓库外临时文件，不加入 git。

回复：成功时列出所选标签和一句判断依据；需要澄清时只写问题。
