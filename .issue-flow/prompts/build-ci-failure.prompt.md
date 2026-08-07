目标：定位 CI failure intake 创建的失败 issue，并在需要时发布 build PR/MR。

执行：
- 先阅读 issue body 中的 Failure Context、Agent Triage Request、Log Summary、Validation，以及链接的 run/job/step。
- 先判断根因类别：repository regression、workflow config、provider permission、secret/variable、runner/environment、transient infrastructure、false positive。
- 不要信任 intake summary 是最终根因；必须结合日志、相关提交、PR/MR、workflow 配置和仓库状态判断。
- 该类 issue intake 默认是 `type::ops`，不要在未定位前改成 bug。
- 只有确认是仓库代码回归时，才把 `type::ops` 改成 `type::bug`，并继续按 build 修复代码。
- 如果根因是 workflow/provider/secret/variable/runner/environment/transient/external，保持 `type::ops`。
- 如果需要外部权限、secret、runner 或服务恢复，设置 `status::suspend`，不要留下 `status::active` + `flow::build` 继续空转。
- 如果确认是误报或无可执行事项，设置 `status::drop` 并评论说明。
- 如果确认已恢复（例如 rerun 通过），设置 `status::done` 并评论说明验证依据。
- 只有确认需要代码或配置修改时才改文件并提交 build PR/MR。
- 如果根因是 provider 权限、secret/variable、runner/environment、瞬时基础设施或误报，不要硬改业务代码；改合适标签、留下说明 comment，或只做最小必要配置修复。
- 提交前按运行时提供的仓库上下文创建或切换到工作分支，不要直接在基准分支提交。
- 按仓库规则修改、验证、提交，然后使用统一 CLI 提交 build PR/MR。

PR body 写清 Source issue、Root cause、Fix、Validation。
PR body 写入仓库外临时文件（例如 `mktemp`），通过 `issue-flow pr submit ... --body-file` 提交，不要加入 git。
