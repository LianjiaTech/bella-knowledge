const { ACTION_FIELDS, REASONING_EFFORTS, validateActionObject, validateActionExecutionConfig } = require('../../../domain/action-execution.cjs');
const DEFAULT_AGENT = 'codex';

function readValue(source, key) {
  if (source && Object.prototype.hasOwnProperty.call(source, key) && source[key] !== null) {
    return source[key];
  }
  return undefined;
}

function resolveActionExecution(action, options = {}, config = {}, configPath = '.issue-flow/config.json') {
  const actions = validateActionExecutionConfig(config, configPath);
  const actionConfig = actions[action];
  const defaults = actions.defaults;
  const explicit = {};
  for (const field of ACTION_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(options, field) && options[field] !== undefined && options[field] !== null) {
      explicit[field] = options[field];
    }
  }
  validateActionObject(explicit, 'runtime options');

  const values = {};
  const sources = {};
  for (const field of ACTION_FIELDS) {
    const candidates = [
      ['options', readValue(explicit, field)],
      [`actions.${action}`, readValue(actionConfig, field)],
      ['actions.defaults', readValue(defaults, field)],
    ];
    let selected = candidates.find(([, value]) => value !== undefined);
    if (!selected && field === 'agent') {
      if (process.env.AGENTRIX_ISSUE_FLOW_AGENT) {
        selected = ['AGENTRIX_ISSUE_FLOW_AGENT', process.env.AGENTRIX_ISSUE_FLOW_AGENT];
      }
      if (!selected && process.env.AGENTRIX_AGENT) selected = ['AGENTRIX_AGENT', process.env.AGENTRIX_AGENT];
      if (!selected) selected = ['built-in', DEFAULT_AGENT];
    }
    if (selected) {
      values[field] = selected[1];
      sources[field] = selected[0];
    }
  }
  return { action, values, sources };
}

module.exports = {
  ACTION_FIELDS,
  REASONING_EFFORTS,
  validateActionExecutionConfig,
  resolveActionExecution,
};
