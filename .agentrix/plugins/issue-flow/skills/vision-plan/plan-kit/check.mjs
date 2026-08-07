#!/usr/bin/env node

import { existsSync, readFileSync, statSync } from "node:fs"
import { dirname, resolve } from "node:path"

const SECTION_TYPES = new Set([
  "summary", "solution-summary", "architecture", "dependency-graph", "deployment",
  "runtime-flow", "sequence", "state-machine", "data-flow", "swimlane", "user-journey",
  "tree", "component-tree", "erd", "matrix", "path-matrix", "permission-matrix",
  "compatibility-matrix", "option-comparison", "risk-control", "validation-matrix",
  "traceability", "responsibility-matrix", "state-action", "failure-handling", "timeline",
  "implementation-steps", "implementation-dag", "rollout", "screen-flow", "wireframe",
  "chart", "custom-html", "change-set", "contract", "risk-register", "validation", "evidence", "cards", "diagram",
])
const GRAPH_TYPES = new Set([
  "architecture", "dependency-graph", "deployment", "runtime-flow", "state-machine",
  "data-flow", "rollout", "screen-flow", "component-tree", "implementation-dag",
])
const ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_:-]*$/
const CHART_VARIANTS = new Set(["bar", "horizontal-bar", "column", "line", "area", "donut", "pie"])
const CUSTOM_HTML_FILE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*\.html$/i
const args = process.argv.slice(2)
const artifactInput = args[0]
const briefIndex = args.indexOf("--brief")
const briefInput = briefIndex >= 0 ? args[briefIndex + 1] : ""

if (!artifactInput) {
  console.error("Usage: node check.mjs <artifact-json> [--brief <temporary-visual-brief-path>]")
  console.log("FAIL: missing artifact JSON path")
  process.exit(1)
}

const failures = []
const warnings = []
const artifactPath = resolve(artifactInput)
let data

if (!existsSync(artifactPath)) failures.push(`missing artifact JSON: ${artifactPath}`)
else {
  try {
    data = JSON.parse(readFileSync(artifactPath, "utf8"))
  } catch (error) {
    failures.push(`artifact is not valid JSON: ${error.message}`)
  }
}

function text(value) {
  return typeof value === "string" || typeof value === "number" ? String(value).trim() : ""
}

function uniqueIds(items, location) {
  const seen = new Set()
  for (const [index, item] of items.entries()) {
    const id = text(item && item.id)
    if (!id) failures.push(`${location}[${index}] is missing id`)
    else if (!ID_PATTERN.test(id)) failures.push(`${location}[${index}] has invalid id: ${id}`)
    else if (seen.has(id)) failures.push(`${location} contains duplicate id: ${id}`)
    else seen.add(id)
  }
  return seen
}

function validateBrief() {
  if (!briefInput) {
    failures.push("Plan validation requires --brief <temporary-visual-brief-path>")
    return
  }
  const briefPath = resolve(briefInput)
  if (!existsSync(briefPath)) {
    failures.push(`missing temporary visual brief: ${briefPath}`)
    return
  }
  const brief = readFileSync(briefPath, "utf8")
  const fields = ["Core outcome", "Main contradiction", "Primary visual model", "Model justification"]
  for (const field of fields) {
    const pattern = new RegExp(`\\*\\*${field}\\*\\*`, "i")
    const match = brief.match(pattern)
    if (!match) failures.push(`temporary visual brief is missing **${field}**`)
    else {
      const content = brief.slice(match.index + match[0].length, match.index + match[0].length + 400)
        .split(/\n\s*[-*]?\s*\*\*/)[0]
        .replace(/^[\s:*_()—–-]+/, "")
        .trim()
      if (!content) failures.push(`temporary visual brief field **${field}** is empty`)
    }
  }
}

function validateForbiddenPresentation(value, location = "") {
  if (!value || typeof value !== "object") return
  if (Array.isArray(value)) {
    value.forEach((entry, index) => validateForbiddenPresentation(entry, `${location}[${index}]`))
    return
  }
  for (const [key, entry] of Object.entries(value)) {
    const next = location ? `${location}.${key}` : key
    if (["html", "css", "js", "script", "style"].includes(key.toLowerCase())) failures.push(`presentation code field is not allowed: ${next}`)
    if (key === "id" && (!text(entry) || !ID_PATTERN.test(text(entry)))) failures.push(`path-safe id is required: ${next}`)
    validateForbiddenPresentation(entry, next)
  }
}

function validateDecision() {
  const decisions = Array.isArray(data.decisions) ? data.decisions : []
  if (!decisions.length) failures.push("Decision must contain at least one decisions[] item")
  uniqueIds(decisions, "decisions")
  for (const decision of decisions) {
    const id = text(decision && decision.id) || "unknown"
    if (!text(decision && (decision.question || decision.title))) failures.push(`Decision ${id} is missing question`)
    const options = Array.isArray(decision && decision.options) ? decision.options : []
    if (decision && decision.type === "choice") {
      if (options.length < 2) failures.push(`Decision ${id} choice needs at least two options`)
      const optionIds = uniqueIds(options, `decisions.${id}.options`)
      const recommended = text(decision.recommendedOptionId || decision.recommended)
      if (!recommended) failures.push(`Decision ${id} choice is missing recommendedOptionId`)
      else if (!optionIds.has(recommended)) failures.push(`Decision ${id} recommendedOptionId does not match an option: ${recommended}`)
    }
  }
}

function validateGraphSection(section) {
  const nodes = Array.isArray(section.nodes || section.elements || section.states || section.screens || section.tasks)
    ? section.nodes || section.elements || section.states || section.screens || section.tasks
    : []
  const edges = Array.isArray(section.edges || section.relationships || section.transitions || section.connections)
    ? section.edges || section.relationships || section.transitions || section.connections
    : []
  const nodeIds = uniqueIds(nodes, `sections.${section.id}.nodes`)
  if (!nodes.length) failures.push(`Graph section ${section.id} has no nodes`)
  edges.forEach((edge, index) => {
    const source = text(edge && (edge.sourceId || edge.from || edge.source))
    const target = text(edge && (edge.destinationId || edge.to || edge.target))
    if (!source || !nodeIds.has(source)) failures.push(`sections.${section.id}.edges[${index}] has unknown source: ${source || "(empty)"}`)
    if (!target || !nodeIds.has(target)) failures.push(`sections.${section.id}.edges[${index}] has unknown target: ${target || "(empty)"}`)
    if (!text(edge && (edge.label || edge.description || edge.trigger || edge.name))) warnings.push(`sections.${section.id}.edges[${index}] has no relationship label`)
  })
}

function validateSequenceSection(section) {
  const participants = Array.isArray(section.participants || section.actors) ? section.participants || section.actors : []
  const messages = Array.isArray(section.messages || section.steps) ? section.messages || section.steps : []
  if (participants.length < 2) failures.push(`Sequence section ${section.id} needs at least two participants`)
  if (!messages.length) failures.push(`Sequence section ${section.id} has no messages`)
  const participantIds = uniqueIds(participants, `sections.${section.id}.participants`)
  uniqueIds(messages, `sections.${section.id}.messages`)
  messages.forEach((message, index) => {
    const source = text(message && (message.sourceId || message.from || message.source))
    const target = text(message && (message.destinationId || message.to || message.target))
    if (!source || !participantIds.has(source)) failures.push(`sections.${section.id}.messages[${index}] has unknown source: ${source || "(empty)"}`)
    if (!target || !participantIds.has(target)) failures.push(`sections.${section.id}.messages[${index}] has unknown target: ${target || "(empty)"}`)
  })
}

function validatePlan() {
  validateBrief()
  if (!data.core || typeof data.core !== "object" || Array.isArray(data.core)) failures.push("Plan must contain a core object")
  else if (!text(data.core.outcome || data.core.goal || data.core.summary)) failures.push("Plan core must describe the outcome")
  const sections = Array.isArray(data.sections) ? data.sections : []
  if (!sections.length) failures.push("Plan must contain sections[]")
  uniqueIds(sections, "sections")
  let hasSummary = false
  let hasValidation = false
  for (const section of sections) {
    const id = text(section && section.id) || "unknown"
    const type = text(section && section.type)
    if (!SECTION_TYPES.has(type)) failures.push(`Plan section ${id} uses unsupported type: ${type || "(empty)"}`)
    if (type === "summary" || type === "solution-summary") hasSummary = true
    if (type === "validation" || type === "validation-matrix") hasValidation = true
    if (GRAPH_TYPES.has(type) || type === "diagram" && text(section.variant) !== "sequence") validateGraphSection(section)
    if (type === "sequence" || type === "diagram" && text(section.variant) === "sequence") validateSequenceSection(section)
    if ((type === "matrix" || type.endsWith("-matrix")) && (!Array.isArray(section.columns) || !Array.isArray(section.rows))) failures.push(`Matrix section ${id} needs columns[] and rows[]`)
    if (type === "chart") {
      const variant = text(section.variant) || "bar"
      if (!CHART_VARIANTS.has(variant)) failures.push(`Chart section ${id} uses unsupported variant: ${variant}`)
      const items = Array.isArray(section.items) ? section.items : []
      if (!items.length) failures.push(`Chart section ${id} needs items[]`)
      uniqueIds(items, `sections.${id}.items`)
      items.forEach((item, index) => {
        if (!Number.isFinite(Number(item && item.value))) failures.push(`sections.${id}.items[${index}] needs a numeric value`)
      })
    }
    if (type === "custom-html") {
      const file = text(section.file)
      if (!CUSTOM_HTML_FILE_PATTERN.test(file)) failures.push(`Custom HTML section ${id} needs a same-directory .html file name`)
      else {
        const demoPath = resolve(dirname(artifactPath), file)
        if (!existsSync(demoPath) || !statSync(demoPath).isFile()) failures.push(`Custom HTML section ${id} file does not exist: ${demoPath}`)
      }
    }
  }
  if (!hasSummary) failures.push("Plan must include a summary or solution-summary section")
  if (!hasValidation) failures.push("Plan must include a validation or validation-matrix section")
}

if (data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) failures.push("artifact root must be an object")
  else {
    if (data.schemaVersion !== 1) failures.push("schemaVersion must be 1")
    if (data.artifact !== "decision" && data.artifact !== "plan") failures.push('artifact must be "decision" or "plan"')
    if (!data.meta || typeof data.meta !== "object" || !text(data.meta.title)) failures.push("meta.title is required")
    validateForbiddenPresentation(data)
    if (data.artifact === "decision") validateDecision()
    if (data.artifact === "plan") validatePlan()
  }
}

for (const warning of warnings) console.warn(`WARNING: ${warning}`)
for (const failure of failures) console.error(`ERROR: ${failure}`)
if (failures.length) {
  console.log(`FAIL: ${failures.length} error(s), ${warnings.length} warning(s)`)
  process.exit(1)
}
console.log(`PASS: JSON artifact is renderable (${warnings.length} warning(s))`)
