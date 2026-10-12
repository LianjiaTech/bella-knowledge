针对当前 issue 产出实现方案并提交方案 PR/MR，不改代码。方案写给审阅者：读完应能判断方向、边界和验收方式，不必再翻代码。

- 能从仓库确认的事实不要问用户；仍缺仓库无法推断的关键事实时，用 `issue apply` 转到 `flow::clarify`，在回复中直接提问，给出你的推荐和选项。
- issue 列出待定决策时，逐条给出结论和理由；方案围绕这些决策组织，而不是罗列步骤。
- 按 Plan template 写入 Plan output file，切到运行时提供的工作分支提交。
- PR body 只写 Summary（3–5 条关键决策）和 Review focus（需要审阅者拍板的问题及你的默认选择）；Source issue 与 Plan file 由 CLI 生成。PR body 写入仓库外临时文件，通过 `issue-flow pr submit plan --body-file` 发布，不要加入 git。

回复：成功时给出方案路径和 PR/MR URL；阻塞时说明阻塞点和需要用户做什么。
