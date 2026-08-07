# Optimization Plan 产物协议

将分析结果写入运行时指定的 `optimization-data.json`。只输出 JSON，不生成 Markdown 或 HTML。

```json
{
  "schemaVersion": 1,
  "artifact": "optimization",
  "target": {
    "summary": "当前问题的简述",
    "cause": [
      "项目中缺少关键外部行为的验证要求",
      "可逆操作方案未先定义共享状态不变量"
    ]
  },
  "proposals": [
    {
      "id": "stable-proposal-id",
      "kind": "project-change",
      "title": "方案标题",
      "solution": "具体改进以及它如何消除根因",
      "validation": ["证明同类任务可首次完成的验证方式"],
      "issue": {
        "title": "可直接执行的 Issue 标题",
        "body": "完整、独立、可执行的 Issue 描述",
        "type": "type::debt",
        "priority": "priority::p2",
        "size": "size::M",
        "flow": "flow::build",
        "labels": []
      }
    }
  ]
}
```

## 字段约束

- `target.summary`：用一句话简述当前问题，最多 120 个字符。
- `target.cause`：包含 1–3 条原因，每条最多 80 个字符。只写人能直接理解的原因结论，不写 Task ID、sequence、事件编号、读取了什么消息或审阅流水账。
- `proposals[].id`：在后续修改中保持稳定，只使用字母、数字、下划线、冒号或连字符。
- `proposals[].kind`：项目内可执行改动使用 `project-change`；需要当前项目开发者补齐仓库外或人工管理能力时使用 `project-developer-feedback`；`.issue-flow` 体系内的公共能力缺陷使用 `issue-flow-feedback`。
- `solution`：写具体落点和作用机制，不写“加强理解”“提高准确率”等空泛表述。
- `validation`：至少一项，验证改进能消除同类人工介入。
- `issue.type`：只能是 `type::feature`、`type::bug`、`type::debt`、`type::ops`、`type::docs`。
- `issue.priority`：只能是 `priority::p0` 至 `priority::p3`。
- `issue.size`：只能是 `size::XS`、`size::S`、`size::M`、`size::L`、`size::XL`。
- `project-change` 的 `issue.flow`：只能是 `flow::plan` 或 `flow::build`；`type::docs` 必须使用 `flow::build`。
- `project-developer-feedback`：只用于 Agent 无法在当前仓库安全实施、必须由当前项目维护者完成的环境、工具链、凭据、组织基础设施或运行配置建议。不得包含 `issue`，页面只展示建议和验证方式，不提供复制或操作按钮。
- `issue-flow-feedback`：只用于 Issue Flow 上下文传递，或 `.issue-flow` 体系内的流程、Skill、模板、脚本等公共能力缺陷；`issue.type` 必须是 `type::bug`，`issue.flow` 必须是 `flow::triage`。
- `issue.labels`：只放额外的非托管标签，不要写托管标签。

`project-change` 由系统添加 `status::active` 与 `automation::build`；`project-developer-feedback` 不创建 Issue；`issue-flow-feedback` 的手动提交建议标签包含 `status::active` 与 `automation::off`。

每个 `project-change` 和 `issue-flow-feedback` 必须包含完整 `issue`；`project-developer-feedback` 必须省略 `issue`。严格使用上述字段，不要添加执行状态或结果字段。
