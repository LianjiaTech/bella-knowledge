<!-- issue-flow:automation-optimization source-issue={{sourceIssueNumber}} -->

## 优化目标

让同类任务在目标阶段尽量一次完成，减少可避免的人工介入。

## 来源任务

- 来源 Issue：#{{sourceIssueNumber}} {{sourceIssueTitle}}
- 优化阶段与 Turns：

{{phaseTurns}}

## 分析范围

基于来源 Issue 在以上阶段的完整 Task 和 TaskEvent，解释 Agent 首次为什么没有完成，而不只是复述人工后来要求补做什么。

## 期望产出

- 有 TaskEvent、代码、文档或测试证据支撑的原因；
- 能长期消除原因的最小改进，可能是需求说明、业务或项目文档、项目规范、代码结构、测试、项目工具，或者 Issue Flow 开发者 Bug 反馈；
- 只有执行方法可以迁移到同类任务时，才提炼不依赖本次类名、字段名和具体用例的项目规范；
- 能验证同类任务可减少人工介入的复现场景。

## 验收标准

1. 每项结论都能定位到具体 Task 和事件序号。
2. 根因解释 Agent 当时为什么会作出原判断，以及现有信息或验证为什么没有阻止它。
3. 优化改动有明确落点，不强制修改 `.issue-flow/instructions.md`。
4. 区分可由 Agent 落地的项目改进、仅需告知当前项目开发者的建议、Issue Flow 开发者反馈、无需长期沉淀的问题和必须保留的人工业务决策。
