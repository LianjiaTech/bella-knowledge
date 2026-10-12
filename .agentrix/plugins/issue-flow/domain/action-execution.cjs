const ACTION_NAMES = ['defaults', 'triage', 'plan', 'build', 'review', 'general'];
const ACTION_FIELDS = ['agent', 'model', 'reasoningEffort'];
const REASONING_EFFORTS = new Set(['low', 'medium', 'high', 'xhigh', 'max']);

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function validateActionObject(value, fieldPath) {
  if (value === null || value === undefined) return {};
  if (!isPlainObject(value)) throw new Error(`${fieldPath} must be an object.`);
  for (const [field, fieldValue] of Object.entries(value)) {
    if (!ACTION_FIELDS.includes(field)) throw new Error(`${fieldPath}.${field} is not a supported action execution field.`);
    if (fieldValue === null) continue;
    if (typeof fieldValue !== 'string' || !fieldValue.trim()) throw new Error(`${fieldPath}.${field} must be a non-empty string or null.`);
    if (field === 'reasoningEffort' && !REASONING_EFFORTS.has(fieldValue)) throw new Error(`${fieldPath}.reasoningEffort must be one of ${[...REASONING_EFFORTS].join(', ')}.`);
  }
  return value;
}

function validateActionExecutionConfig(config = {}, configPath = '.issue-flow/config.json') {
  if (!isPlainObject(config)) throw new Error(`${configPath}.agentrix must be an object.`);
  const actions = config.actions;
  if (actions === undefined || actions === null) return {};
  if (!isPlainObject(actions)) throw new Error(`${configPath}.agentrix.actions must be an object.`);
  for (const [action, value] of Object.entries(actions)) validateActionObject(value, `${configPath}.agentrix.actions.${action}`);
  return actions;
}

function actionExecutionSnapshot(config) {
  if (!isPlainObject(config)) throw new Error('Configuration must be an object.');
  const actions = validateActionExecutionConfig(config.agentrix ?? {});
  const explicit = {};
  const preview = {};
  for (const action of ACTION_NAMES) {
    explicit[action] = {};
    preview[action] = {};
    for (const field of ACTION_FIELDS) {
      const value = actions[action]?.[field];
      const fallback = actions.defaults?.[field];
      if (value != null) explicit[action][field] = value.trim();
      preview[action][field] = value != null
        ? { value: value.trim(), source: action }
        : action !== 'defaults' && fallback != null
          ? { value: fallback.trim(), source: 'defaults' }
          : { source: 'runtime' };
    }
  }
  return { explicit, preview };
}

function mergeActionExecution(config, input) {
  actionExecutionSnapshot(config);
  if (!isPlainObject(input) || Object.keys(input).some((action) => !ACTION_NAMES.includes(action))) throw new Error('Unsupported action configuration.');
  const next = JSON.parse(JSON.stringify(config));
  for (const action of ACTION_NAMES) {
    if (!isPlainObject(input[action])) throw new Error(`${action} must be an object.`);
    const normalized = {};
    for (const [field, value] of Object.entries(input[action])) {
      if (!ACTION_FIELDS.includes(field)) throw new Error(`Unsupported field: ${field}`);
      if (value == null || value === '') continue;
      if (typeof value !== 'string') throw new Error(`${action}.${field} must be a string.`);
      if (value.trim()) normalized[field] = value.trim();
    }
    validateActionObject(normalized, action);
    const original = config.agentrix?.actions?.[action];
    const previous = Object.fromEntries(Object.entries(original || {}).filter(([, value]) => value != null).map(([field, value]) => [field, value.trim()]));
    if (ACTION_FIELDS.every((field) => previous[field] === normalized[field])) continue;
    if (Object.keys(normalized).length) {
      next.agentrix ??= {};
      next.agentrix.actions ??= {};
      next.agentrix.actions[action] = normalized;
    } else if (next.agentrix?.actions) delete next.agentrix.actions[action];
  }
  return next;
}

module.exports = { ACTION_NAMES, ACTION_FIELDS, REASONING_EFFORTS, validateActionObject, validateActionExecutionConfig, actionExecutionSnapshot, mergeActionExecution };
