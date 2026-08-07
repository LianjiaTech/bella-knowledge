const SOURCE_MARKER_PATTERN = /<!--\s*issue-flow:source\s+([^>]*?)\s*-->\s*/i;

function normalizeMarkerValue(value) {
  return String(value || '').trim().replace(/[^\w:./-]+/g, '-');
}

function resolveSourceTaskId(options = {}, env = process.env) {
  return normalizeMarkerValue(
    options.sourceTaskId ||
    options.taskId ||
    options.agentrixTaskId ||
    options.runId ||
    env.AGENTRIX_TASK_ID ||
    ''
  );
}

function resolveSourceAgent(options = {}, env = process.env) {
  return normalizeMarkerValue(
    options.sourceAgent ||
    options.agent ||
    env.AGENTRIX_ISSUE_FLOW_AGENT ||
    env.AGENTRIX_AGENT ||
    env.AGENTRIX_AGENT_NAME ||
    ''
  );
}

function resolveSourceRuntime(options = {}, env = process.env) {
  return normalizeMarkerValue(
    options.sourceRuntime ||
    options.runtime ||
    env.ISSUE_FLOW_SOURCE_RUNTIME ||
    ''
  );
}

function buildSourceMarker(options = {}, env = process.env) {
  const fields = [];
  const sourceTaskId = resolveSourceTaskId(options, env);
  const sourceAgent = resolveSourceAgent(options, env);
  const sourceRuntime = resolveSourceRuntime(options, env);
  if (sourceTaskId) fields.push(`source_task_id=${sourceTaskId}`);
  if (sourceAgent) fields.push(`source_agent=${sourceAgent}`);
  if (sourceRuntime) fields.push(`source_runtime=${sourceRuntime}`);
  return fields.length ? `<!-- issue-flow:source ${fields.join(' ')} -->` : '';
}

function parseSourceMarker(body = '') {
  const match = String(body || '').match(SOURCE_MARKER_PATTERN);
  if (!match) return {};
  const parsed = {};
  const fields = String(match[1] || '').trim().split(/\s+/);
  for (const field of fields) {
    const eq = field.indexOf('=');
    if (eq <= 0) continue;
    const key = field.slice(0, eq);
    const value = field.slice(eq + 1);
    if (key === 'source_task_id' || key === 'source_agent' || key === 'source_runtime') {
      parsed[key] = value;
    }
  }
  return parsed;
}

function hasSourceMarker(body = '') {
  return SOURCE_MARKER_PATTERN.test(String(body || ''));
}

function upsertSourceMarker(body, options = {}, env = process.env) {
  const marker = buildSourceMarker(options, env);
  const content = String(body || '').trimStart();
  if (!marker) return content;
  if (SOURCE_MARKER_PATTERN.test(content)) {
    return content.replace(SOURCE_MARKER_PATTERN, `${marker}\n`);
  }
  return `${marker}\n${content}`.trimEnd();
}

module.exports = {
  buildSourceMarker,
  hasSourceMarker,
  normalizeMarkerValue,
  parseSourceMarker,
  resolveSourceAgent,
  resolveSourceRuntime,
  resolveSourceTaskId,
  upsertSourceMarker,
};
