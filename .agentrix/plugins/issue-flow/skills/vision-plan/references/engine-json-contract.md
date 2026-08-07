# Issue Flow Visual Engine JSON Manual

Read this manual before writing Decision or Visual Plan JSON.

This file defines the canonical authoring contract. It intentionally omits Engine-internal fallback aliases and presentation details. Generate only the fields documented here.

## Notation

The definitions use TypeScript-like notation to describe JSON:

- `field: Type` is required.
- `field?: Type` is optional.
- `A | B` means either value is allowed.
- `Text` is a JSON string.
- `TextList` is `Text | Text[]`.
- `Id` matches `^[A-Za-z0-9][A-Za-z0-9_:-]*$`.

Use stable semantic IDs such as `engine`, `provider-api`, and `review-submit`. Do not use dots, spaces, generated UUIDs, hashes, or array-position names.

Global rules:

1. Use `schemaVersion: 1`.
2. Set `artifact` to `decision` or `plan`.
3. Give every independently reviewable object a stable `id`.
4. Keep IDs unique inside their collection.
5. Never include keys named `html`, `css`, `js`, `script`, or `style`. A `custom-html` section references a separate file through `file`; it never embeds code in JSON.
6. Never provide SVG, coordinates, dimensions, colors, classes, selectors, or layout instructions.
7. Use only the canonical fields below. Unknown fields may not be rendered.

## Shared Types

```ts
type Text = string
type TextList = Text | Text[]
type Id = string

type ArtifactMeta = {
  title: Text
  description?: TextList
}

type BadgeFields = {
  kind?: Text | Text[]
  status?: Text | Text[]
  owner?: Text | Text[]
  technology?: Text | Text[]
  protocol?: Text | Text[]
}

type PathMembership = {
  paths?: Id[]
}

type PathDefinition = {
  id: Id
  label: Text
}
```

Badge fields are optional semantic metadata. Do not add badges merely for decoration.

## Decision Artifact

```ts
type DecisionArtifact = {
  schemaVersion: 1
  artifact: "decision"
  meta: ArtifactMeta
  context?: {
    summary?: TextList
  }
  decisions: DecisionItem[]
}

type DecisionItem = {
  id: Id
  type: "choice" | "approval"
  question: Text
  description?: TextList
  criteria?: TextList
  recommendedOptionId?: Id
  options?: DecisionOption[]
  consequence?: TextList
}

type DecisionOption = {
  id: Id
  label: Text
  description?: TextList
  consequences?: TextList
}
```

Rules:

- `decisions[]` must not be empty.
- `choice` requires at least two options and a `recommendedOptionId` matching one option ID.
- `approval` is a real yes/no confirmation and normally omits `options`.
- Criteria and consequences support a decision; they are not separate approval items.

## Plan Artifact

```ts
type PlanArtifact = {
  schemaVersion: 1
  artifact: "plan"
  meta: ArtifactMeta
  core: PlanCore
  sections: PlanSection[]
}

type PlanCore = {
  outcome: TextList
  contradiction?: TextList
  boundary?: TextList
  recommendation?: TextList
}

type SectionBase = {
  id: Id
  type: PlanSectionType
  title: Text
  description?: TextList
  variant?: Text
}

type PlanSection =
  | SummarySection
  | GraphSection
  | SequenceSection
  | SwimlaneSection
  | MatrixSection
  | TimelineSection
  | TreeSection
  | ErdSection
  | WireframeSection
  | CustomHtmlSection
  | ChartSection
  | CardsSection
```

Rules:

- `core.outcome` is required.
- `sections[]` must not be empty and defines render order.
- Include one `summary` or `solution-summary` section.
- Include one `validation` or `validation-matrix` section.
- Use the smallest set of sections that explains the solution and closes validation.

## Section Catalog

| Renderer | Supported `type` values |
| --- | --- |
| Summary | `summary`, `solution-summary` |
| Relationship graph | `architecture`, `dependency-graph`, `deployment`, `runtime-flow`, `data-flow`, `state-machine`, `rollout`, `screen-flow`, `component-tree`, `implementation-dag` |
| Generic graph/sequence | `diagram` |
| Sequence | `sequence` |
| Swimlane | `swimlane`, `user-journey` |
| Matrix | `matrix`, `path-matrix`, `permission-matrix`, `compatibility-matrix`, `validation-matrix`, `responsibility-matrix` |
| Timeline | `timeline`, `implementation-steps` |
| Tree | `tree` |
| Data model | `erd` |
| Interface structure | `wireframe` |
| Interactive frontend Demo | `custom-html` |
| Numeric comparison | `chart` |
| Structured cards | `option-comparison`, `risk-control`, `traceability`, `state-action`, `failure-handling`, `change-set`, `contract`, `risk-register`, `validation`, `evidence`, `cards` |

`diagram` uses the graph contract by default. Set `variant: "sequence"` to use the sequence contract.

## Summary

Types: `summary`, `solution-summary`.

```ts
type SummarySection = SectionBase & {
  type: "summary" | "solution-summary"
}
```

The Engine renders standardized outcome, contradiction, boundary, and recommendation cards from `plan.core`.

## Relationship Graph

Types: `architecture`, `dependency-graph`, `deployment`, `runtime-flow`, `data-flow`, `state-machine`, `rollout`, `screen-flow`, `component-tree`, `implementation-dag`, or `diagram` without `variant: "sequence"`.

```ts
type GraphSection = SectionBase & {
  type:
    | "architecture" | "dependency-graph" | "deployment"
    | "runtime-flow" | "data-flow" | "state-machine"
    | "rollout" | "screen-flow" | "component-tree"
    | "implementation-dag" | "diagram"
  nodes: GraphNode[]
  edges: GraphEdge[]
  groups?: GraphGroup[]
  paths?: PathDefinition[]
}

type GraphNode = BadgeFields & PathMembership & {
  id: Id
  name: Text
  type?: Text
  description?: Text
  responsibility?: Text
  groupId?: Id
}

type GraphEdge = PathMembership & {
  id: Id
  sourceId: Id
  destinationId: Id
  label: Text
  protocol?: Text
  technology?: Text
  kind?: "sync" | "async" | "event" | "success" | "fail" | "error"
}

type GraphGroup = {
  id: Id
  label: Text
}
```

Rules:

- Include at least one node.
- Every edge endpoint references a node ID in the same section.
- Use concrete relationship labels.
- `groupId` references `groups[].id`.
- Use groups only for meaningful system, trust, deployment, persistence, or ownership boundaries.
- The Engine calculates all positions.

For a state machine, still use canonical `nodes` and `edges`; encode state and transition meaning through names, descriptions, and edge labels.

The Engine selects layout from `type`:

- `deployment`: group-first boundary columns.
- `state-machine`: circular state layout that exposes cycles and return paths.
- `component-tree`: top-down hierarchy.
- `rollout`: ordered stage layout.
- Other graph types: left-to-right layered topology.

## Sequence

Types: `sequence`, or `diagram` with `variant: "sequence"`.

```ts
type SequenceSection = SectionBase & {
  type: "sequence" | "diagram"
  variant?: "sequence"
  participants: SequenceParticipant[]
  messages: SequenceMessage[]
  fragments?: SequenceFragment[]
  paths?: PathDefinition[]
}

type SequenceParticipant = BadgeFields & {
  id: Id
  name: Text
}

type SequenceMessage = PathMembership & {
  id: Id
  sourceId: Id
  destinationId: Id
  label: Text
  async?: boolean
  kind?: "sync" | "async" | "event" | "callback"
}

type SequenceFragment = {
  id: Id
  type: "loop" | "alt" | "opt" | "group"
  label: Text
  startId: Id
  endId: Id
}
```

Rules:

- Include at least two participants and one message.
- Message endpoints reference participant IDs.
- Use fragments only when a loop, alternative, optional branch, or grouped range changes understanding.

## Swimlane

Types: `swimlane`, `user-journey`.

```ts
type SwimlaneSection = SectionBase & {
  type: "swimlane" | "user-journey"
  lanes: Swimlane[]
}

type Swimlane = {
  id: Id
  owner: Text
  description?: TextList
  steps: SwimlaneStep[]
}

type SwimlaneStep = BadgeFields & {
  id: Id
  title: Text
  description?: TextList
  action?: TextList
}
```

Use swimlanes when responsibility and handoff matter more than system topology.

## Matrix

Types: `matrix`, `path-matrix`, `permission-matrix`, `compatibility-matrix`, `validation-matrix`, `responsibility-matrix`.

```ts
type MatrixSection = SectionBase & {
  type:
    | "matrix" | "path-matrix" | "permission-matrix"
    | "compatibility-matrix" | "validation-matrix"
    | "responsibility-matrix"
  rowHeader?: Text
  columns: MatrixColumn[]
  rows: MatrixRow[]
}

type MatrixColumn = {
  id: Id
  label: Text
}

type MatrixRow = {
  id: Id
  label: Text
  cells?: MatrixCell[]
  values?: Record<Id, MatrixCell | Text | number>
}

type MatrixCell = {
  value: Text | number
  tone?: "ok" | "warn" | "fail" | "neutral"
}
```

Choose one cell representation per section:

- `cells[]` follows column order.
- `values` keys match `columns[].id`.

Use `tone` only for semantic status.

## Timeline

Types: `timeline`, `implementation-steps`.

```ts
type TimelineSection = SectionBase & {
  type: "timeline" | "implementation-steps"
  items: TimelineItem[]
}

type TimelineItem = BadgeFields & {
  id: Id
  title: Text
  description?: TextList
  action?: TextList
  outcome?: TextList
}
```

Use ordered items to express implementation sequence, rollout, or milestones.

## Tree

Type: `tree`.

```ts
type TreeSection = SectionBase & {
  type: "tree"
  items: TreeNode[]
}

type TreeNode = BadgeFields & {
  id: Id
  title: Text
  description?: TextList
  children?: TreeNode[]
}
```

Use `tree` for hierarchy. Use `component-tree` when directional dependencies matter; it uses the graph contract.

## ERD

Type: `erd`.

```ts
type ErdSection = SectionBase & {
  type: "erd"
  entities: Entity[]
}

type Entity = BadgeFields & {
  id: Id
  name: Text
  fields: EntityField[]
}

type EntityField = {
  id: Id
  name: Text
  type?: Text
  primaryKey?: boolean
  required?: boolean
}
```

Use a separate graph when directional entity relationships matter.

## Wireframe

Type: `wireframe`.

```ts
type WireframeSection = SectionBase & {
  type: "wireframe"
  screens: WireframeScreen[]
}

type WireframeScreen = {
  id: Id
  title: Text
  regions: WireframeRegion[]
}

type WireframeRegion = {
  id: Id
  title: Text
  description?: TextList
}
```

Describe hierarchy, information, state, and behavior—not visual styling or pixel geometry.

## Custom HTML Demo

Type: `custom-html`.

```ts
type CustomHtmlSection = SectionBase & {
  type: "custom-html"
  file: Text
}
```

Use this section when reviewers need to judge a proposed frontend as a real, operable page. When the product is page-centric and includes multiple user interactions or responsive requirements, the Plan should include a `custom-html` Demo. Use `wireframe` alone when static structure is sufficient or the page is only a small supporting detail.

Rules:

- `file` is a file name such as `demo.html`, not a path. It must match `^[A-Za-z0-9][A-Za-z0-9._-]*\.html$`.
- Put the HTML file in the same directory as `plan.json.isv` and commit both files.
- The HTML page must be self-contained. Put its CSS and JavaScript inside the page; do not reference sibling files or repository build output.
- The JSON remains the semantic Plan source. Use the Demo only for frontend appearance and interaction; keep implementation facts, boundaries, contracts, risks, and validation in normal sections.
- The Engine renders the page in a sandboxed iframe and provides the section shell and expand control. The page cannot access Issue Flow credentials or its parent document.
- Review comments target the `custom-html` section as a whole through `sections.<section-id>`.
- Typical uses include interaction-heavy dashboards, forms, onboarding flows, responsive product pages, and other page-centric work with several user actions or states to review.

Minimal reference:

```json
{ "id": "frontend-demo", "type": "custom-html", "title": "交互 Demo", "file": "demo.html" }
```

## Chart

Type: `chart`.

```ts
type ChartSection = SectionBase & {
  type: "chart"
  variant: "bar" | "horizontal-bar" | "column" | "line" | "area" | "donut" | "pie"
  items: ChartItem[]
}

type ChartItem = {
  id: Id
  label: Text
  value: number
}
```

Variants:

- `bar` or `horizontal-bar`: horizontal magnitude comparison.
- `column`: vertical magnitude comparison.
- `line`: ordered trend.
- `area`: ordered trend with filled magnitude.
- `donut`: part-to-whole comparison with a total.
- `pie`: part-to-whole comparison.

Use charts only for real numeric evidence. `donut` and `pie` require at least one positive value.

## Structured Cards

Types: `option-comparison`, `risk-control`, `traceability`, `state-action`, `failure-handling`, `change-set`, `contract`, `risk-register`, `validation`, `evidence`, `cards`.

```ts
type CardsSection = SectionBase & {
  type:
    | "option-comparison" | "risk-control" | "traceability"
    | "state-action" | "failure-handling" | "change-set"
    | "contract" | "risk-register" | "validation"
    | "evidence" | "cards"
  items: CardItem[]
}

type CardItem = BadgeFields & {
  id: Id
  title: Text
  description?: TextList
  details?: TextList
  refs?: TextList
}
```

Use one focused fact per card. `refs[]` contains Engine review paths to related JSON objects.

## Path Filtering

Graph and sequence sections may declare `paths?: PathDefinition[]`. Assign membership with `paths?: Id[]` on nodes, edges, or messages.

Use path filters only when reviewers need to compare success, failure, retry, fallback, permission, or rollout paths.

## Review Paths

The Engine generates review anchors. Never write anchors or selectors into JSON.

```text
meta
context
core.outcome
core.contradiction
core.boundary
core.recommendation
decisions.<decision-id>
decisions.<decision-id>.options.<option-id>
sections.<section-id>
sections.<section-id>.<collection>.<item-id>
sections.<section-id>.rows.<row-id>.cells.<cell-index>
sections.<section-id>.rows.<row-id>.values.<column-id>
```

Nested collections continue the same pattern. Use these paths in `refs[]`, and ensure each reference resolves to a real JSON object.

## Validation

The checker and submit script enforce:

- Valid artifact root, version, kind, and title.
- Path-safe IDs and unique IDs in required collections.
- Valid Decision choices and recommendations.
- Supported Plan section types.
- Required summary and validation sections.
- Valid graph and sequence endpoints.
- Required matrix columns and rows.
- No presentation-code fields in JSON.
- Valid, existing same-directory HTML files for `custom-html` sections.
- Required temporary reflection brief for Plan validation.

Run:

```bash
node .agentrix/plugins/issue-flow/skills/vision-plan/plan-kit/check.mjs <decision.json.isv>
node .agentrix/plugins/issue-flow/skills/vision-plan/plan-kit/check.mjs <plan.json.isv> --brief <temporary-visual-brief-path>
```
