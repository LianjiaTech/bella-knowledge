针对当前 issue 产出可审阅的根因与修复方案，并提交方案 PR/MR。

要求：
- 先列出所有已知症状，包括 issue 中的描述和仓库中能定位到的相关行为。
- 提出根因前，必须能写出一句话：`我认为根因是 <具体文件/函数/条件>，因为 <证据>`。
- 根因必须解释所有已知症状；如果只能解释一部分，不要写方案 PR/MR，改为澄清。
- 若仍缺少仓库无法推断的关键事实，使用统一 CLI 的 `issue apply` 将 issue 转到 `flow::clarify`，然后直接提问。
- 若根因和修复方法足够明确，按运行时提供的 Plan template 结构，把方案写入 Plan output file。
- 提交前按运行时提供的仓库上下文创建或切换到工作分支，不要直接在基准分支提交。
- 提交方案文件，写 PR body，然后使用统一 CLI 提交 plan PR/MR。

澄清提问：
- 直接提问，不解释标签变化。
- 问题要体现你已经做过排查：给出当前最可能判断、推荐默认选项和可选路径。
- 优先选择题，例如环境、复现频率、期望行为、是否接受某种修复边界。

PR 要求：
- title 必须关联 issue 号，推荐 `Plan #<number>: <short title>`。
- PR body 必须包含 Source issue、Plan file、Root cause summary、Review focus。
- PR body 写入仓库外临时文件（例如 `mktemp`），通过 `issue-flow pr submit ... --body-file` 提交，不要加入 git。

回复：
- 成功：只说明方案文件路径、PR/MR URL、issue 已进入审批。
- 需要澄清：直接提出问题；优先给选项和你的推荐。
- 凭证阻塞：说明阻塞点和建议用户采取的行动。
