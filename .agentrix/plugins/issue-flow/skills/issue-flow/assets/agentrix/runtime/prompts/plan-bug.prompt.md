针对当前 bug 产出根因与修复方案并提交方案 PR/MR，不改代码。方案写给审阅者：读完应能确认根因成立、修复位置合适、回归有保障。

- 先列出所有已知症状。提出根因前必须能写出：`我认为根因是 <具体文件/函数/条件>，因为 <证据>`，且根因能解释全部症状。
- 能从仓库确认的事实不要问用户；根因只能解释部分症状，或缺少仓库无法推断的关键事实时，用 `issue apply` 转到 `flow::clarify`，在回复中直接提问，给出当前最可能的判断、你的推荐和选项。
- 按 Plan template 写入 Plan output file，切到运行时提供的工作分支提交。
- PR body 只写 Root cause summary（一句话根因及关键证据）和 Review focus（需要审阅者拍板的问题及你的默认选择）；Source issue 与 Plan file 由 CLI 生成。PR body 写入仓库外临时文件，通过 `issue-flow pr submit plan --body-file` 发布，不要加入 git。

回复：成功时给出方案路径和 PR/MR URL；阻塞时说明阻塞点和需要用户做什么。
