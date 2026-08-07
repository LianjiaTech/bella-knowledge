# runtimes/
> L2 | 父级: /CLAUDE.md

成员清单
agentrix.cjs: Agentrix runtime adapter，CommonJS，按 issue 标签选择并组装 prompt、run args、resume args 与 task comment。
CLAUDE.md: 本目录的 L2 地图，记录 runtime 适配层职责与边界。

依赖边界
agentrix.cjs -> ../providers.cjs、../provenance.cjs、assets/agentrix/runtime/*。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
