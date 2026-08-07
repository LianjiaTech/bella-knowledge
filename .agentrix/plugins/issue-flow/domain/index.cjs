const labels = require('../skills/issue-flow/scripts/labels.cjs');
const provenance = require('../skills/issue-flow/scripts/provenance.cjs');

const SOURCE_ISSUE_MARKER_PATTERN = /<!--\s*issue-flow:source-issue=(\d+)\s*-->/i;
const PLAN_ARTIFACT_MARKER_PATTERN = /<!--\s*issue-flow:plan-artifact\s+artifact=(decision|plan|optimization)\s+format=(json|markdown)\s+(?:repo=[^\s]+\s+)?issue=(\d+)\s+branch=([^\s]+)\s+commit=([^\s]+)\s+path=([^\s]+)\s*-->/i;
const OPTIMIZATION_SOURCE_PATTERN = /<!--\s*issue-flow:automation-optimization\s+source-issue=(\d+)\s*-->/i;
const OPTIMIZATION_PROPOSAL_PATTERN = /<!--\s*issue-flow:optimization-proposal\s+optimization-issue=(\d+)\s+source-issue=(\d+)\s+proposal=([^\s>]+)(?:\s+action=([^\s>]+))?(?:\s+child-issue=(\d+))?\s*-->/i;
const VISUAL_SECTION_TYPES = new Set([
  'summary', 'solution-summary', 'architecture', 'dependency-graph', 'deployment',
  'runtime-flow', 'sequence', 'state-machine', 'data-flow', 'swimlane', 'user-journey',
  'tree', 'component-tree', 'erd', 'matrix', 'path-matrix', 'permission-matrix',
  'compatibility-matrix', 'option-comparison', 'risk-control', 'validation-matrix',
  'traceability', 'responsibility-matrix', 'state-action', 'failure-handling',
  'timeline', 'implementation-steps', 'implementation-dag', 'rollout', 'screen-flow',
  'wireframe', 'chart', 'change-set', 'contract', 'risk-register', 'validation',
  'evidence', 'cards', 'diagram', 'custom-html',
]);
const VISUAL_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_:-]*$/;
const VISUAL_CHART_VARIANTS = new Set(['bar', 'horizontal-bar', 'column', 'line', 'area', 'donut', 'pie']);
const CUSTOM_HTML_FILE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*\.html$/i;
const MANAGED_LABEL_PREFIXES = Object.values(labels.MANAGED_LABEL_GROUPS).map((group) => group.prefix);
const OPTIMIZATION_TRACE_DETAIL_PATTERN = /(?:task[-_:][a-z0-9]|task\s+id|sequence|taskevent)/i;

function normalizeLabelName(label) {
  if (typeof label === 'string') return label;
  return label && (label.name || label.title) || '';
}

function normalizeLabels(input = []) {
  return (Array.isArray(input) ? input : []).map(normalizeLabelName).filter(Boolean);
}

function prefixedLabelValue(input, prefix, normalize = (value) => value, allowed) {
  for (const label of normalizeLabels(input)) {
    if (!label.toLowerCase().startsWith(prefix.toLowerCase())) continue;
    const value = normalize(label.slice(prefix.length));
    if (!allowed || allowed.has(value)) return value;
  }
  return '';
}

function managedLabelValue(input, key, normalize = (value) => value) {
  const group = labels.MANAGED_LABEL_GROUPS[key];
  if (!group) throw new Error(`Unknown managed label group: ${key}`);
  const allowed = new Set(group.labels.map((label) => normalize(label.name.slice(group.prefix.length))));
  return prefixedLabelValue(input, group.prefix, normalize, allowed);
}

function issueFlow(input = []) {
  return managedLabelValue(input, 'flow', (value) => value.toLowerCase());
}

function issueStatus(attributes = {}, input = []) {
  const explicit = managedLabelValue(input, 'status', (value) => value.toLowerCase());
  if (String(attributes.state || '').toLowerCase() === 'closed') return explicit === 'done' ? 'done' : 'drop';
  if (explicit) return explicit;
  return issueFlow(input) === 'suspend' ? 'suspend' : 'active';
}

function computeManagedLabelChanges(currentInput, desiredByKey = {}, clearKeys = []) {
  const currentLabels = normalizeLabels(currentInput);
  const desiredLabels = Object.values(desiredByKey).filter(Boolean);
  const keys = [...new Set([...Object.keys(desiredByKey), ...clearKeys])];
  const prefixes = keys.map((key) => {
    const group = labels.MANAGED_LABEL_GROUPS[key];
    if (!group) throw new Error(`Unknown managed label group: ${key}`);
    if (desiredByKey[key] && !group.labels.some((label) => label.name === desiredByKey[key])) {
      throw new Error(`${key} must be one of: ${group.labels.map((label) => label.name).join(', ')}`);
    }
    return group.prefix;
  });
  const labelsToRemove = currentLabels.filter((label) => prefixes.some((prefix) => label.startsWith(prefix)) && !desiredLabels.includes(label));
  const labelsToAdd = desiredLabels.filter((label) => !currentLabels.includes(label));
  return { labelsToAdd: [...new Set(labelsToAdd)], labelsToRemove: [...new Set(labelsToRemove)] };
}

function applyManagedLabels(currentInput, desiredByKey = {}, clearKeys = []) {
  const currentLabels = normalizeLabels(currentInput);
  const { labelsToAdd, labelsToRemove } = computeManagedLabelChanges(currentLabels, desiredByKey, clearKeys);
  const remove = new Set(labelsToRemove);
  return [...new Set([...currentLabels.filter((label) => !remove.has(label)), ...labelsToAdd])];
}

function replaceLabelsByPrefix(currentInput, desiredByPrefix = {}) {
  const prefixes = Object.keys(desiredByPrefix);
  const next = normalizeLabels(currentInput).filter((label) => !prefixes.some((prefix) => label.startsWith(prefix)));
  for (const value of Object.values(desiredByPrefix)) if (value) next.push(value);
  return [...new Set(next)];
}

function buildSourceIssueMarker(issueNumber) {
  return `<!-- issue-flow:source-issue=${Number(issueNumber)} -->`;
}

function sourceIssueNumber(body = '') {
  const match = String(body).match(SOURCE_ISSUE_MARKER_PATTERN);
  return match ? Number.parseInt(match[1], 10) : 0;
}

function upsertSourceIssueMarker(body, issueNumber) {
  const marker = buildSourceIssueMarker(issueNumber);
  const content = String(body || '').trimStart();
  return SOURCE_ISSUE_MARKER_PATTERN.test(content)
    ? content.replace(SOURCE_ISSUE_MARKER_PATTERN, marker)
    : `${marker}\n${content}`;
}

function buildPlanArtifactMarker(input = {}) {
  return `<!-- issue-flow:plan-artifact artifact=${input.artifact} format=${input.format || 'json'} issue=${input.issueNumber} branch=${input.branch} commit=${input.commit || input.commitSha} path=${input.path || input.artifactPath || input.entryPath} -->`;
}

function parsePlanArtifactMarker(body = '') {
  const match = String(body).match(PLAN_ARTIFACT_MARKER_PATTERN);
  return match ? {
    artifact: match[1].toLowerCase(), format: match[2].toLowerCase(), issueNumber: Number.parseInt(match[3], 10),
    branch: match[4], commit: match[5], path: match[6],
  } : undefined;
}

function optimizationSourceIssueNumber(body = '') {
  const match = String(body).match(OPTIMIZATION_SOURCE_PATTERN);
  return match ? Number.parseInt(match[1], 10) : 0;
}

function buildOptimizationProposalMarker({ optimizationIssueNumber, sourceIssueNumber: sourceNumber, proposalId, action, childIssueNumber }) {
  return `<!-- issue-flow:optimization-proposal optimization-issue=${optimizationIssueNumber} source-issue=${sourceNumber} proposal=${proposalId}${action ? ` action=${action}` : ''}${childIssueNumber ? ` child-issue=${childIssueNumber}` : ''} -->`;
}

function parseOptimizationProposalMarker(body = '') {
  const match = String(body).match(OPTIMIZATION_PROPOSAL_PATTERN);
  return match ? {
    optimizationIssueNumber: Number.parseInt(match[1], 10), sourceIssueNumber: Number.parseInt(match[2], 10),
    proposalId: match[3], action: match[4] || 'created',
    ...(match[5] ? { childIssueNumber: Number.parseInt(match[5], 10) } : {}),
  } : undefined;
}

function issueBody(issue = {}) {
  return issue.body || issue.description || '';
}

function childOptimizationState(issue = {}) {
  const issueLabels = normalizeLabels(issue.labels);
  if (issueLabels.includes('status::done')) return 'completed';
  if (issueLabels.includes('status::drop')) return 'cancelled';
  if (issueLabels.includes('flow::build') || issueLabels.includes('flow::approve')) return 'executing';
  return 'created';
}

function deriveOptimizationProposalStates(data, optimizationIssueNumber, comments = [], issues = []) {
  const ignored = new Set(comments.map((comment) => parseOptimizationProposalMarker(comment && comment.body)).filter((marker) => marker && marker.optimizationIssueNumber === optimizationIssueNumber && marker.action === 'ignored').map((marker) => marker.proposalId));
  const children = new Map();
  for (const issue of issues) {
    const marker = parseOptimizationProposalMarker(issueBody(issue));
    if (marker && marker.optimizationIssueNumber === optimizationIssueNumber && !children.has(marker.proposalId)) children.set(marker.proposalId, issue);
  }
  return data.proposals.map((proposal) => {
    const childIssue = children.get(proposal.id);
    return {
      id: proposal.id,
      kind: proposal.kind || 'project-change',
      state: childIssue ? childOptimizationState(childIssue) : ignored.has(proposal.id) ? 'ignored' : 'pending',
      childIssue: childIssue ? { number: childIssue.number, title: childIssue.title, state: childIssue.state, webUrl: childIssue.webUrl || '' } : null,
    };
  });
}

function allOptimizationProposalsTerminal(states = []) {
  const executable = states.filter((item) => item.kind === 'project-change');
  return states.length > 0 && executable.every((item) => ['ignored', 'completed', 'cancelled'].includes(item.state));
}

function resolvePlanArtifactTransition(artifact) {
  if (artifact === 'decision') return { kind: 'decision', flow: 'flow::plan' };
  if (artifact === 'optimization') return { kind: 'optimization' };
  return { kind: 'plan', flow: 'flow::build' };
}

function resolveMergedPullRequestTransition(inputLabels = [], pullRequest = {}) {
  const transitions = {
    plan: { label: 'mr-by::plan', flow: 'flow::build' },
    build: { label: 'mr-by::build', status: 'status::done', clearFlow: true },
  };
  const matches = Object.entries(transitions).filter(([, transition]) => normalizeLabels(inputLabels).includes(transition.label));
  if (!matches.length) return undefined;
  if (matches.length > 1) throw new Error(`Pull request has multiple issue-flow source labels: ${matches.map(([, transition]) => transition.label).join(', ')}`);
  const [kind, transition] = matches[0];
  const artifact = kind === 'plan' ? parsePlanArtifactMarker(pullRequest.body) : undefined;
  if (artifact) {
    const artifactTransition = resolvePlanArtifactTransition(artifact.artifact);
    return { ...artifactTransition, label: transition.label, artifact: artifact.artifact, format: artifact.format };
  }
  return { kind, ...transition, ...(artifact ? { artifact: artifact.artifact, format: artifact.format } : {}) };
}

function assertOnlyFields(value, allowed, label) {
  const unknown = Object.keys(value).filter((key) => !allowed.includes(key));
  if (unknown.length) throw new Error(`${label} contains unsupported fields: ${unknown.join(', ')}`);
}

function collectIds(items, location) {
  const ids = new Set();
  for (const [index, item] of items.entries()) {
    const id = String(item && item.id || '').trim();
    if (!id) throw new Error(`${location}[${index}] must have an id`);
    if (!VISUAL_ID_PATTERN.test(id)) throw new Error(`${location}[${index}] has an invalid id: ${id}`);
    if (ids.has(id)) throw new Error(`${location} contains duplicate id: ${id}`);
    ids.add(id);
  }
  return ids;
}

function validateOptimizationArtifact(data) {
  assertOnlyFields(data, ['schemaVersion', 'artifact', 'target', 'proposals'], 'Optimization artifact');
  if (!data.target || typeof data.target !== 'object' || Array.isArray(data.target)) throw new Error('Optimization artifact must contain target');
  assertOnlyFields(data.target, ['summary', 'cause'], 'Optimization target');
  const summary = String(data.target.summary || '').trim();
  if (!summary) throw new Error('Optimization target must contain summary');
  if (summary.length > 120) throw new Error('Optimization target summary must not exceed 120 characters');
  if (!Array.isArray(data.target.cause) || data.target.cause.length < 1 || data.target.cause.length > 3) throw new Error('Optimization target cause must contain 1 to 3 items');
  for (const [index, cause] of data.target.cause.entries()) {
    const value = String(cause || '').trim();
    if (!value) throw new Error(`Optimization target cause[${index}] must not be empty`);
    if (value.length > 80) throw new Error(`Optimization target cause[${index}] must not exceed 80 characters`);
    if (OPTIMIZATION_TRACE_DETAIL_PATTERN.test(value)) throw new Error(`Optimization target cause[${index}] must not contain Task IDs, sequence, or event trace details`);
  }
  if (!Array.isArray(data.proposals) || !data.proposals.length) throw new Error('Optimization artifact must contain proposals');
  const ids = new Set();
  const types = new Set(['type::feature', 'type::bug', 'type::debt', 'type::ops', 'type::docs']);
  const kinds = new Set(['project-change', 'project-developer-feedback', 'issue-flow-feedback']);
  const priorities = new Set(['priority::p0', 'priority::p1', 'priority::p2', 'priority::p3']);
  const sizes = new Set(['size::XS', 'size::S', 'size::M', 'size::L', 'size::XL']);
  const flows = new Set(['flow::plan', 'flow::build']);
  for (const [index, proposal] of data.proposals.entries()) {
    const id = String(proposal && proposal.id || '').trim();
    if (!VISUAL_ID_PATTERN.test(id)) throw new Error(`proposals[${index}] must have a path-safe id`);
    if (ids.has(id)) throw new Error(`Optimization artifact contains duplicate proposal id: ${id}`);
    ids.add(id);
    assertOnlyFields(proposal, ['id', 'kind', 'title', 'solution', 'validation', 'issue'], `Proposal ${id}`);
    if (!kinds.has(proposal.kind)) throw new Error(`Proposal ${id} has invalid kind`);
    if (!String(proposal.title || '').trim()) throw new Error(`Proposal ${id} must contain title`);
    if (!String(proposal.solution || '').trim()) throw new Error(`Proposal ${id} must contain solution`);
    if (!Array.isArray(proposal.validation) || !proposal.validation.length || proposal.validation.some((item) => !String(item || '').trim())) throw new Error(`Proposal ${id} must contain validation`);
    if (proposal.kind === 'project-developer-feedback') {
      if (proposal.issue !== undefined) throw new Error(`Proposal ${id} project developer feedback must not contain issue`);
      continue;
    }
    const issue = proposal.issue;
    if (!issue || typeof issue !== 'object' || Array.isArray(issue)) throw new Error(`Proposal ${id} must contain issue`);
    assertOnlyFields(issue, ['title', 'body', 'type', 'priority', 'size', 'flow', 'labels'], `Proposal ${id} issue`);
    if (!String(issue.title || '').trim() || !String(issue.body || '').trim()) throw new Error(`Proposal ${id} issue must contain title and body`);
    if (!types.has(issue.type)) throw new Error(`Proposal ${id} has invalid issue type`);
    if (!priorities.has(issue.priority)) throw new Error(`Proposal ${id} has invalid issue priority`);
    if (!sizes.has(issue.size)) throw new Error(`Proposal ${id} has invalid issue size`);
    if (proposal.kind === 'project-change' && !flows.has(issue.flow)) throw new Error(`Proposal ${id} has invalid project issue flow`);
    if (proposal.kind === 'project-change' && issue.type === 'type::docs' && issue.flow !== 'flow::build') throw new Error(`Proposal ${id} type::docs must use flow::build`);
    if (proposal.kind === 'issue-flow-feedback' && issue.type !== 'type::bug') throw new Error(`Proposal ${id} Issue Flow feedback must use type::bug`);
    if (proposal.kind === 'issue-flow-feedback' && issue.flow !== 'flow::triage') throw new Error(`Proposal ${id} Issue Flow feedback must use flow::triage`);
    const issueLabels = issue.labels === undefined ? [] : issue.labels;
    if (!Array.isArray(issueLabels) || issueLabels.some((label) => !String(label || '').trim())) throw new Error(`Proposal ${id} issue.labels must contain non-empty strings`);
    const managed = issueLabels.find((label) => MANAGED_LABEL_PREFIXES.some((prefix) => String(label).startsWith(prefix)));
    if (managed) throw new Error(`Proposal ${id} issue.labels cannot contain managed label: ${managed}`);
  }
  return data;
}

function validateVisualArtifactData(data, artifact, options = {}) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error(`Visual ${artifact} artifact must contain a JSON object`);
  if (data.schemaVersion !== 1) throw new Error(`Visual ${artifact} artifact schemaVersion must be 1`);
  if (data.artifact !== artifact) throw new Error(`Visual ${artifact} artifact field must equal "${artifact}"`);
  if (artifact === 'optimization') return validateOptimizationArtifact(data);
  if (!data.meta || typeof data.meta !== 'object' || !String(data.meta.title || '').trim()) throw new Error(`Visual ${artifact} artifact must contain meta.title`);
  const forbidden = [];
  const invalidIds = [];
  const visit = (value, location = '') => {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value)) return value.forEach((entry, index) => visit(entry, `${location}[${index}]`));
    for (const [key, entry] of Object.entries(value)) {
      const next = location ? `${location}.${key}` : key;
      if (['html', 'css', 'js', 'script', 'style'].includes(key.toLowerCase())) forbidden.push(next);
      if (key === 'id' && (!String(entry || '').trim() || !VISUAL_ID_PATTERN.test(String(entry).trim()))) invalidIds.push(next);
      visit(entry, next);
    }
  };
  visit(data);
  if (forbidden.length) throw new Error(`Visual ${artifact} JSON cannot contain presentation code fields: ${forbidden.join(', ')}`);
  if (invalidIds.length) throw new Error(`Visual ${artifact} JSON contains invalid path-safe ids: ${invalidIds.join(', ')}`);
  if (artifact === 'decision') {
    if (!Array.isArray(data.decisions) || !data.decisions.length) throw new Error('Decision JSON must contain at least one decisions[] item');
    collectIds(data.decisions, 'decisions');
    for (const decision of data.decisions) {
      const id = String(decision && decision.id || '').trim();
      if (!String(decision.question || decision.title || '').trim()) throw new Error(`Decision ${id} must contain a question`);
      const decisionOptions = Array.isArray(decision.options) ? decision.options : [];
      if (decision.type === 'choice' && decisionOptions.length < 2) throw new Error(`Decision ${id} choice must contain at least two options`);
      if (decision.type === 'choice') {
        const optionIds = collectIds(decisionOptions, `decisions.${id}.options`);
        const recommended = String(decision.recommendedOptionId || decision.recommended || '').trim();
        if (!recommended) throw new Error(`Decision ${id} choice must contain recommendedOptionId`);
        if (!optionIds.has(recommended)) throw new Error(`Decision ${id} recommendedOptionId does not match an option: ${recommended}`);
      }
    }
    return data;
  }
  if (!Array.isArray(data.sections) || !data.sections.length) throw new Error('Plan JSON must contain at least one sections[] item');
  if (!data.core || typeof data.core !== 'object' || Array.isArray(data.core)) throw new Error('Plan JSON must contain a core object');
  if (!String(data.core.outcome || data.core.goal || data.core.summary || '').trim()) throw new Error('Plan core must describe the outcome');
  collectIds(data.sections, 'sections');
  let hasSummary = false;
  let hasValidation = false;
  for (const section of data.sections) {
    const id = String(section && section.id || '').trim();
    const type = String(section && section.type || '').trim();
    if (!VISUAL_SECTION_TYPES.has(type)) throw new Error(`Plan section ${id} uses unsupported type: ${type || '(empty)'}`);
    if (type === 'summary' || type === 'solution-summary') hasSummary = true;
    if (type === 'validation' || type === 'validation-matrix') hasValidation = true;
    const graph = ['architecture', 'dependency-graph', 'deployment', 'runtime-flow', 'state-machine', 'data-flow', 'rollout', 'screen-flow', 'component-tree', 'implementation-dag'].includes(type) || type === 'diagram' && String(section.variant || '').trim() !== 'sequence';
    if (graph) {
      const nodes = section.nodes || section.elements || section.states || section.screens || section.tasks || [];
      const edges = section.edges || section.relationships || section.transitions || section.connections || [];
      if (!Array.isArray(nodes) || !nodes.length) throw new Error(`Graph section ${id} must contain nodes`);
      const nodeIds = collectIds(nodes, `sections.${id}.nodes`);
      if (!Array.isArray(edges)) throw new Error(`Graph section ${id} edges must be an array`);
      for (const [edgeIndex, edge] of edges.entries()) {
        const source = String(edge && (edge.sourceId || edge.from || edge.source) || '').trim();
        const target = String(edge && (edge.destinationId || edge.to || edge.target) || '').trim();
        if (!source || !nodeIds.has(source)) throw new Error(`sections.${id}.edges[${edgeIndex}] has unknown source: ${source || '(empty)'}`);
        if (!target || !nodeIds.has(target)) throw new Error(`sections.${id}.edges[${edgeIndex}] has unknown target: ${target || '(empty)'}`);
      }
      collectIds(edges, `sections.${id}.edges`);
    }
    const sequence = type === 'sequence' || type === 'diagram' && String(section.variant || '').trim() === 'sequence';
    if (sequence) {
      const participants = Array.isArray(section.participants || section.actors) ? section.participants || section.actors : [];
      const messages = Array.isArray(section.messages || section.steps) ? section.messages || section.steps : [];
      if (participants.length < 2) throw new Error(`Sequence section ${id} must contain at least two participants`);
      if (!messages.length) throw new Error(`Sequence section ${id} must contain messages`);
      const participantIds = collectIds(participants, `sections.${id}.participants`);
      collectIds(messages, `sections.${id}.messages`);
      for (const [messageIndex, message] of messages.entries()) {
        const source = String(message && (message.sourceId || message.from || message.source) || '').trim();
        const target = String(message && (message.destinationId || message.to || message.target) || '').trim();
        if (!participantIds.has(source)) throw new Error(`sections.${id}.messages[${messageIndex}] has unknown source: ${source || '(empty)'}`);
        if (!participantIds.has(target)) throw new Error(`sections.${id}.messages[${messageIndex}] has unknown target: ${target || '(empty)'}`);
      }
    }
    if (type === 'chart') {
      const variant = String(section.variant || 'bar').trim();
      if (!VISUAL_CHART_VARIANTS.has(variant)) throw new Error(`Chart section ${id} uses unsupported variant: ${variant}`);
      if (!Array.isArray(section.items) || !section.items.length) throw new Error(`Chart section ${id} must contain items`);
      collectIds(section.items, `sections.${id}.items`);
      for (const [itemIndex, item] of section.items.entries()) if (!Number.isFinite(Number(item && item.value))) throw new Error(`sections.${id}.items[${itemIndex}] must contain a numeric value`);
    }
    if (type === 'custom-html') {
      const file = String(section.file || '').trim();
      if (!CUSTOM_HTML_FILE_PATTERN.test(file)) throw new Error(`Custom HTML section ${id} must reference a same-directory .html file name`);
      if (options.customHtmlFileExists && !options.customHtmlFileExists(file)) throw new Error(`Custom HTML section ${id} file does not exist: ${file}`);
    }
  }
  if (!hasSummary) throw new Error('Plan JSON must include a summary or solution-summary section');
  if (!hasValidation) throw new Error('Plan JSON must include a validation or validation-matrix section');
  return data;
}

module.exports = {
  ...labels,
  ...provenance,
  allOptimizationProposalsTerminal,
  applyManagedLabels,
  buildOptimizationProposalMarker,
  buildPlanArtifactMarker,
  buildSourceIssueMarker,
  childOptimizationState,
  computeManagedLabelChanges,
  deriveOptimizationProposalStates,
  issueFlow,
  issueStatus,
  managedLabelValue,
  normalizeLabelName,
  normalizeLabels,
  optimizationSourceIssueNumber,
  parseOptimizationProposalMarker,
  parsePlanArtifactMarker,
  prefixedLabelValue,
  replaceLabelsByPrefix,
  resolveMergedPullRequestTransition,
  resolvePlanArtifactTransition,
  sourceIssueNumber,
  upsertSourceIssueMarker,
  validateOptimizationArtifact,
  validateVisualArtifactData,
};
