const fs = require('node:fs');

const GITLAB_BRIDGE_PREFIX = 'GITLAB_BRIDGE_';

function readJsonFile(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function parseJsonArray(value) {
  if (!value) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function parsePositiveInteger(value) {
  const parsed = Number.parseInt(String(value || ''), 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function gitlabBridgeValue(env = process.env, name) {
  return env[`${GITLAB_BRIDGE_PREFIX}${name}`] || '';
}

function splitProjectPath(projectPath) {
  const parts = String(projectPath || '').split('/').filter(Boolean);
  return {
    owner: parts.slice(0, -1).join('/'),
    name: parts[parts.length - 1] || '',
  };
}

function gitlabProjectPath(env = process.env) {
  if (env.CI_PROJECT_PATH) {
    return env.CI_PROJECT_PATH;
  }
  if (env.GITLAB_PROJECT_PATH) {
    return env.GITLAB_PROJECT_PATH;
  }
  if (env.GITLAB_BRIDGE_REPOSITORY) {
    return env.GITLAB_BRIDGE_REPOSITORY;
  }
  return '';
}

function labelTitlesFromEnv(env = process.env) {
  const parsed = parseJsonArray(gitlabBridgeValue(env, 'LABELS_JSON'));
  if (parsed) {
    return parsed.map((label) => {
      if (typeof label === 'string') {
        return label;
      }
      if (label && typeof label.title === 'string') {
        return label.title;
      }
      if (label && typeof label.name === 'string') {
        return label.name;
      }
      return '';
    }).filter(Boolean);
  }
  return String(gitlabBridgeValue(env, 'LABELS') || '')
    .split(',')
    .map((label) => label.trim())
    .filter(Boolean);
}

function labelsFromEnv(env = process.env) {
  return labelTitlesFromEnv(env).map((title) => ({ title }));
}

function buildGitlabBridgeProject(env = process.env) {
  const projectPath = gitlabProjectPath(env);
  const { name } = splitProjectPath(projectPath);
  return {
    id: parsePositiveInteger(env.CI_PROJECT_ID),
    path: name,
    path_with_namespace: projectPath,
    default_branch: env.CI_DEFAULT_BRANCH || gitlabBridgeValue(env, 'BASE_REF') || gitlabBridgeValue(env, 'REF_NAME') || env.CI_COMMIT_REF_NAME || '',
    web_url: env.CI_PROJECT_URL || '',
  };
}

function issueProviderAction(action) {
  if (action === 'opened') return 'open';
  if (action === 'closed') return 'close';
  if (action === 'reopened') return 'reopen';
  return action ? 'update' : undefined;
}

function noteProviderAction(action) {
  if (action === 'created') return 'create';
  if (action === 'edited') return 'update';
  if (action === 'deleted') return 'delete';
  return action;
}

function mergeRequestProviderAction(action, env = process.env) {
  if (action === 'opened') return 'open';
  if (action === 'reopened') return 'reopen';
  if (action === 'closed' && gitlabBridgeValue(env, 'PULL_REQUEST_MERGED') === 'true') return 'merge';
  if (action === 'closed') return 'close';
  if (action === 'synchronize') return 'update';
  return action;
}

function buildGitlabBridgeIssuePayload(env = process.env, project = buildGitlabBridgeProject(env)) {
  const action = gitlabBridgeValue(env, 'EVENT_ACTION') || '';
  const issueNumber = parsePositiveInteger(gitlabBridgeValue(env, 'ISSUE_NUMBER'));
  if (!issueNumber) {
    return undefined;
  }

  const issue = {
    iid: issueNumber,
    title: gitlabBridgeValue(env, 'ISSUE_TITLE') || '',
    description: gitlabBridgeValue(env, 'ISSUE_BODY') || '',
    action: issueProviderAction(action),
    state: action === 'closed' ? 'closed' : 'opened',
    url: gitlabBridgeValue(env, 'ISSUE_URL') || '',
    labels: labelsFromEnv(env),
  };

  return {
    event_type: 'issue',
    object_kind: 'issue',
    project,
    object_attributes: issue,
    labels: issue.labels,
    user: {
      username: env.GITLAB_USER_LOGIN || env.GITLAB_USER_NAME || '',
    },
  };
}

function buildGitlabBridgeNotePayload(env = process.env, project = buildGitlabBridgeProject(env)) {
  const action = gitlabBridgeValue(env, 'EVENT_ACTION') || '';
  const eventName = gitlabBridgeValue(env, 'EVENT_NAME');
  const subjectKind = eventName === 'pull_request_review_comment' || gitlabBridgeValue(env, 'PR_NUMBER') ? 'pull_request' : 'issue';
  const noteableType = subjectKind === 'pull_request' ? 'MergeRequest' : 'Issue';
  const issueNumber = parsePositiveInteger(gitlabBridgeValue(env, 'ISSUE_NUMBER'));
  const mergeRequestNumber = parsePositiveInteger(gitlabBridgeValue(env, 'PR_NUMBER'));
  const subjectNumber = subjectKind === 'pull_request' ? mergeRequestNumber : issueNumber;
  if (!subjectNumber) {
    return undefined;
  }

  const payload = {
    event_type: 'note',
    object_kind: 'note',
    project,
    object_attributes: {
      id: parsePositiveInteger(gitlabBridgeValue(env, 'COMMENT_ID')) || gitlabBridgeValue(env, 'COMMENT_ID'),
      action: noteProviderAction(action),
      note: gitlabBridgeValue(env, 'COMMENT_BODY') || '',
      noteable_type: noteableType,
      url: gitlabBridgeValue(env, 'COMMENT_URL') || '',
    },
    user: {
      username: env.GITLAB_USER_LOGIN || env.GITLAB_USER_NAME || '',
      bot: false,
    },
  };

  if (subjectKind === 'pull_request') {
    payload.merge_request = {
      iid: mergeRequestNumber,
      source_branch: gitlabBridgeValue(env, 'HEAD_REF') || '',
      target_branch: gitlabBridgeValue(env, 'BASE_REF') || '',
    };
  } else {
    payload.issue = {
      iid: issueNumber,
      title: gitlabBridgeValue(env, 'ISSUE_TITLE') || '',
      description: gitlabBridgeValue(env, 'ISSUE_BODY') || '',
      state: 'opened',
      url: gitlabBridgeValue(env, 'ISSUE_URL') || '',
      labels: labelsFromEnv(env),
    };
  }

  return payload;
}

function buildGitlabBridgeMergeRequestPayload(env = process.env, project = buildGitlabBridgeProject(env)) {
  const action = gitlabBridgeValue(env, 'EVENT_ACTION') || '';
  const mergeRequestNumber = parsePositiveInteger(gitlabBridgeValue(env, 'PR_NUMBER'));
  if (!mergeRequestNumber) {
    return undefined;
  }

  const merged = gitlabBridgeValue(env, 'PULL_REQUEST_MERGED') === 'true';
  const headSha = gitlabBridgeValue(env, 'HEAD_SHA') || env.CI_COMMIT_SHA;
  const attrs = {
    iid: mergeRequestNumber,
    title: gitlabBridgeValue(env, 'PR_TITLE') || '',
    description: gitlabBridgeValue(env, 'PR_BODY') || '',
    action: mergeRequestProviderAction(action, env),
    state: merged ? 'merged' : action === 'closed' ? 'closed' : 'opened',
    source_branch: gitlabBridgeValue(env, 'HEAD_REF') || '',
    target_branch: gitlabBridgeValue(env, 'BASE_REF') || '',
    last_commit: headSha ? { id: headSha } : undefined,
    url: gitlabBridgeValue(env, 'PR_URL') || '',
    labels: labelsFromEnv(env),
  };

  return {
    event_type: 'merge_request',
    object_kind: 'merge_request',
    project,
    object_attributes: attrs,
    labels: attrs.labels,
    user: {
      username: env.GITLAB_USER_LOGIN || env.GITLAB_USER_NAME || '',
    },
  };
}

function isGitlabBridgeEnv(env = process.env) {
  return Boolean(env.GITLAB_BRIDGE_EVENT_NAME);
}

function buildGitlabBridgePayload(env = process.env) {
  if (!isGitlabBridgeEnv(env)) {
    return undefined;
  }

  const project = buildGitlabBridgeProject(env);
  switch (gitlabBridgeValue(env, 'EVENT_NAME')) {
    case 'issues':
      return buildGitlabBridgeIssuePayload(env, project);
    case 'issue_comment':
    case 'pull_request_review_comment':
      return buildGitlabBridgeNotePayload(env, project);
    case 'pull_request':
    case 'pull_request_review':
      return buildGitlabBridgeMergeRequestPayload(env, project);
    default:
      return undefined;
  }
}

function resolveEventPath(options = {}, env = process.env) {
  return options.event || env.GITHUB_EVENT_PATH || env.GITLAB_EVENT_PATH || env.GITLAB_BRIDGE_EVENT_PATH || '';
}

function loadEventPayload(options = {}, env = process.env) {
  const eventPath = resolveEventPath(options, env);
  if (eventPath) {
    return {
      source: 'file',
      eventPath,
      payload: readJsonFile(eventPath),
    };
  }

  const payload = buildGitlabBridgePayload(env);
  if (payload) {
    return {
      source: 'gitlab-bridge',
      eventPath: '',
      payload,
    };
  }

  return {
    source: 'empty',
    eventPath: '',
    payload: {},
  };
}

module.exports = {
  buildGitlabBridgePayload,
  buildGitlabBridgeIssuePayload,
  buildGitlabBridgeMergeRequestPayload,
  buildGitlabBridgeNotePayload,
  gitlabBridgeValue,
  isGitlabBridgeEnv,
  labelTitlesFromEnv,
  loadEventPayload,
  readJsonFile,
  resolveEventPath,
};
