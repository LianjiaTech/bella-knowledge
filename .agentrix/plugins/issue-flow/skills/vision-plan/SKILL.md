---
name: vision-plan
description: Create Issue Flow Decision and Visual Plan JSON artifacts with separated lifecycle stages, architecture and runtime models, reviewable structured facts, and plan-kit validation.
---

# Vision Plan

Use this skill to create or update Issue Flow review artifacts:

- `.issue-flow/issues/{issue-slug}/decision/data/decision.json.isv` for a pre-plan decision review when the requirement has contradictions, ambiguous scope, or user choices that must be answered before a plan is generated.
- `.issue-flow/issues/{issue-slug}/plan/data/plan.json.isv` for a visual implementation plan.

For newly authored visual artifacts, the `.isv` suffix is the Preview discovery contract regardless of file name or directory. Legacy Markdown and fixed-name JSON previews remain supported for compatibility. Keep the JSON `artifact` field as `decision` or `plan` so the Engine selects the correct review experience without changing the Visual Engine Schema.

The JSON is the semantic source of a review surface, not a document dump. It should help the user understand the proposal faster and help an implementation agent execute it correctly. Issue Flow Engine renders the JSON with fixed components, layout, styles, diagrams, review anchors, comments, and approval controls.

This skill is active only when the source issue has `feature:visual-plan:on`. Without that label, Issue Flow uses the Markdown Plan workflow and this skill must not replace it.

Before writing artifact JSON, read [references/engine-json-contract.md](references/engine-json-contract.md). It is the complete Engine type manual and defines every supported section, field, alias, review path, and validation rule. Do not invent component types or fields outside that contract.

## Hard Rules

1. Start each artifact from its source data.
   - For a plan, write `visual-brief.md` to the absolute temporary path injected by the runtime before writing Plan JSON. Never put it in the repository.
   - For a decision gate, write `decision/data/decision.json.isv`.
   - Treat it as the information architecture contract for the artifact.
   - Do not create presentation code except for an interactive Demo selected under the guidance below.

2. Separate source of truth from presentation.
   - Put plan entities, edges, states, constraints, invariants, risks, and validation scenarios in `plan/data/plan.json.isv`.
   - Put unresolved questions, options, recommendations, criteria, and consequences in `decision/data/decision.json.isv`.
   - The agent owns facts and component selection. Issue Flow Engine owns HTML, CSS, JavaScript, SVG, coordinates, layout, review anchors, and interaction behavior for every built-in component.
   - Never create `decision.html`, `plan/index.html`, artifact CSS, artifact JavaScript, SVG, or copied rendering assets. The only exception is one or more self-contained Demo HTML files explicitly referenced by `custom-html` Plan sections.
   - Encode every important review fact using the structures defined by the Engine manual.

3. Keep the decision gate and implementation plan as separate lifecycle stages.
   - First inspect the requirement for contradictions, ambiguous scope, incompatible constraints, and choices whose answer materially changes the solution.
   - A Decision is justified only by a real unresolved choice that cannot be answered from the issue, repository, comments, or existing conventions. Do not create a Decision merely because Visual Plan is enabled.
   - When such questions exist, create Decision JSON and stop at the decision review gate. Each question needs a recommended option, alternatives, criteria, and consequences.
   - The Engine renders the Decision artifact and submits the answers as a Decision review.
   - After the user answers the gate, treat those answers as settled inputs and generate Plan JSON from them. The Plan should lead with the resulting solution, boundaries, implementation mechanism, and validation.
   - When no blocking question exists, generate the plan directly.
   - Publish a decision with `issue-flow pr submit plan --artifact decision`; publishing keeps the Plan branch/MR open and sets `flow::clarify`.
   - Publish a plan with `issue-flow pr submit plan --artifact plan`; it updates the same Plan branch/MR and sets `flow::approve`.
   - Issue Flow owns publication, review routing, approval state, and provider operations after these commands complete.
   - Decision approval posts an approved review comment on the open Plan MR and resumes the same Plan task. Continue on the existing Plan branch and update the same MR with the Plan artifact.
   - When generating the first Plan after Decision approval, delete the `decision/` directory before committing. The Plan commit and eventual merge must not retain the superseded Decision artifact.
   - Plan approval advances the source issue to Build. Do not bypass the Issue Flow review page.

4. Use diagrams instead of prose when structure is the point.
   - If the text says who calls whom, who owns a business rule, who writes state, where data comes from, where the boundary is, how retries/callbacks flow, or which part cannot do something, draw it.
   - Select the matching Engine section type and provide its nodes, relationships, steps, rows, or entities. Cards and paragraphs may explain a diagram, but must not replace it.

5. Keep language shallow and obvious.
   - Prefer plain words over architecture jargon.
   - Name things by business action, responsibility, state, data, and user-visible behavior.
   - Label the plan for a normal reviewer: "系统边界", "请求路径", "谁写状态", "失败后怎么走", "如何验证".
   - Each section should answer one concrete question. If a sentence needs rereading, rewrite it.

## Interactive Demo

Use a `custom-html` Plan section when reviewers need to understand the proposed frontend as a real, operable page rather than a structural sketch. When the product is page-centric and includes multiple user interactions or responsive requirements, the Plan should include an interactive Demo.

- Create a standalone, self-contained `.html` file in the same directory as `plan.json.isv`.
- Reference only its file name from the section's `file` field; never place HTML, CSS, or JavaScript inside JSON.
- Keep Demo CSS and JavaScript inside that HTML file. Do not add sibling assets or depend on repository build tooling.
- Treat the Demo as a review prototype, not production implementation code. Keep architecture, contracts, risks, and validation in the normal Engine sections.
- The Engine reads the file through the Provider API and renders it in an isolated iframe with a standard container and expand control.
- Use `wireframe` when static information architecture is sufficient or the page is only a small supporting part of the task.
- Use `custom-html` when the page itself is the product surface and reviewers need to exercise several actions, state changes, feedback paths, or responsive layouts before implementation.

## Brief Contract

The temporary `visual-brief.md` must include these fixed field names because `plan-kit/check.mjs` verifies them:

```md
- **Core outcome**: What will be built, in one direct sentence?
- **Main contradiction**: What is the real tension or risk?
- **Primary visual model**: What is the main diagram/view?
- **Model justification**: Why this model reveals the risk better than prose?
- **Supporting views**: What secondary diagrams/tables close gaps?
- **Interaction model**: Static, path toggle, stepper, tabs, or none.
- **Must show / must avoid**: What must be visible, and what should not be included?
```

## Diagram Selection

Use architecture models as a selection guide, not as labels to show the user.

| Review question | Use this view | When to choose it |
| --- | --- | --- |
| What is inside this system, and what is outside? | System boundary diagram | Users, third-party services, external APIs, payments, storage, or trust boundaries matter. |
| Which services/apps/stores own the behavior? | Service/data-store architecture diagram | Most backend/platform plans need this first. It shows applications, data stores, responsibilities, and communication. |
| Which modules inside one service own business rules? | Internal responsibility diagram | Use only when module ownership affects the implementation or refactor risk. |
| What happens at runtime for this scenario? | Runtime flow / sequence / dynamic diagram | Use for callbacks, retries, queues, worker handoff, agent loops, and multi-step workflows. |
| What states can this object move through? | State machine / lifecycle diagram | Use for refunds, jobs, tasks, approvals, sync, retries, and failure recovery. |
| Where does data originate, transform, and persist? | Data flow / provenance diagram | Use for forms, extraction, agent outputs, imports, audit trails, and generated content. |
| Who owns each action across roles or systems? | Responsibility lane diagram | Use when humans, agents, services, or workers share a workflow. |
| Which paths are allowed, denied, blocked, or partial? | Path matrix | Use for permissions, policy, validation gates, fallback paths, and risk coverage. |
| What proves this plan works? | Validation matrix | Use in every plan. Each risk or invariant should have at least one validation row. |

For many software tasks, the right structure is:

1. A system or service/data-store architecture diagram to establish boundaries.
2. A runtime, state, or data-flow diagram to explain the mechanism.
3. A path matrix to expose branches, boundaries, and edge cases.
4. A validation matrix to close the loop.

## Decision Review Method

Decision JSON describes a decision form, not a preface to the Plan and not a workflow report.

Include only:

1. A short statement of the concrete choice that blocks the Plan.
2. One decision card per unresolved choice.
3. For each choice: the recommended option, credible alternatives, decision criteria, and consequences of each option.
4. Only the repository or requirement evidence needed to make that choice.

If removing a field would not change the reviewer's choice, remove it. If there is no unresolved choice after repository inspection, do not generate Decision JSON; generate the Plan directly.

Use a selectable choice only when the reviewer must choose between materially different paths. Use an approval item only for a real yes/no confirmation. Criteria, evidence, consequences, and explanation support the decision; they are not separate approvals.

## Plan Reading Order

Order `sections[]` so the rendered Plan follows this reading order:

1. **核心方案**: one-sentence outcome, main contradiction, chosen boundary, and recommended path.
2. **Architecture diagram**: parts, external systems, data stores, ownership, and calls.
3. **Mechanism diagram**: runtime flow, state machine, data flow, or responsibility lanes.
4. **Implementation details**: module ownership, contracts, state changes, and failure handling.
5. **Implementation handoff**: only the few facts an implementer needs next.
6. **Validation closure**: tests or checks mapped to risks, invariants, and user-visible behavior.

Keep simple tasks simple. If a bug fix has one part and one state change, use one small architecture sketch plus a path matrix; do not build a large framework diagram.

The Engine provides the artifact table of contents and continuous document layout. Give every major section a concise title and keep the JSON section order readable from top to bottom.

## Artifact Authoring

After selecting the review models, use the Engine manual to encode them as artifact JSON.

- Keep one fact in one review object. Do not duplicate facts merely to change presentation.
- Represent branching, merging, loops, ownership, state, and validation as structure rather than prose.
- Select only the components that improve understanding or review confidence.
- Follow the manual exactly for artifact roots, component types, fields, aliases, identities, references, validation, and publication commands.
- Do not invent Engine capabilities or generate presentation code outside the documented `custom-html` Demo exception.

## Validation

Run the checker exactly as documented in the Engine manual before committing and publishing. Fix every failure. Treat warnings as real review risks unless there is a clear reason.
