const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const DEFAULT_PROVIDER = 'github';

function parseJsonText(text) {
  return text ? JSON.parse(text) : undefined;
}

function normalizeProviderName(value) {
  const normalized = String(value || DEFAULT_PROVIDER).trim().toLowerCase();
  if (normalized === 'gh') {
    return 'github';
  }
  if (normalized === 'gl') {
    return 'gitlab';
  }
  if (normalized !== 'github' && normalized !== 'gitlab') {
    throw new Error(`provider must be one of: github, gitlab`);
  }
  return normalized;
}

function detectProvider(options = {}, payload = {}) {
  if (options.provider || process.env.ISSUE_FLOW_PROVIDER) {
    return normalizeProviderName(options.provider || process.env.ISSUE_FLOW_PROVIDER);
  }
  if (process.env.AGENTRIX_PROVIDER) {
    return normalizeProviderName(process.env.AGENTRIX_PROVIDER);
  }
  if (process.env.GITLAB_BRIDGE_EVENT_NAME) {
    return 'gitlab';
  }
  if (options.gitlabUrl || options.gitlabApiUrl || process.env.GITLAB_BASE_URL || process.env.GITLAB_API_URL) {
    return 'gitlab';
  }
  const repoHint = String(options.repo || options.gitlabProject || process.env.GITLAB_PROJECT_PATH || process.env.CI_PROJECT_PATH || '').toLowerCase();
  if (repoHint.includes('gitlab') || process.env.ISSUE_FLOW_GITLAB_TOKEN || process.env.GITLAB_TOKEN || process.env.GL_TOKEN || process.env.GITLAB_PRIVATE_TOKEN) {
    return 'gitlab';
  }
  if (repoHint.includes('github')) {
    return 'github';
  }
  if (payload.object_kind || payload.project || process.env.GITLAB_CI || process.env.CI_PROJECT_PATH) {
    return 'gitlab';
  }
  const remote = parseGitRemoteUrl(options.repo || process.env.CI_REPOSITORY_URL || getGitOriginUrl());
  if (remote.host && remote.host.includes('github')) {
    return 'github';
  }
  if (remote.host) {
    return 'gitlab';
  }
  return 'github';
}

function resolveProvider(options = {}, payload = {}) {
  const name = detectProvider(options, payload);
  return providers[name];
}

function stripGitRemote(value) {
  return String(value || '')
    .trim()
    .replace(/^ssh:\/\/git@[^/]+\//, '')
    .replace(/^git@[^:]+:/, '')
    .replace(/^https?:\/\/[^@/]+@[^/]+\//, '')
    .replace(/^https?:\/\/[^/]+\//, '')
    .replace(/\.git$/, '');
}

function getGitOriginUrl() {
  const result = spawnSync('git', ['config', '--get', 'remote.origin.url'], {
    encoding: 'utf8',
  });
  if (result.error || result.status !== 0) {
    return '';
  }
  return result.stdout.trim();
}

function parseGitRemoteUrl(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return {};
  }

  let match = raw.match(/^git@([^:]+):(.+)$/);
  if (match) {
    return normalizeGitRemoteParts(match[1], match[2]);
  }

  match = raw.match(/^ssh:\/\/(?:[^@/]+@)?([^/]+)\/(.+)$/);
  if (match) {
    return normalizeGitRemoteParts(match[1], match[2]);
  }

  match = raw.match(/^(https?):\/\/(?:[^@/]+@)?([^/]+)\/(.+)$/);
  if (match) {
    return normalizeGitRemoteParts(match[2], match[3], match[1]);
  }

  return {};
}

function normalizeGitRemoteParts(host, remotePath, protocol = 'https') {
  const cleanHost = String(host || '').trim();
  const fullName = String(remotePath || '')
    .replace(/^\/+/, '')
    .replace(/\.git$/, '');
  if (!cleanHost || !fullName) {
    return {};
  }
  return {
    host: cleanHost.toLowerCase(),
    baseUrl: `${protocol}://${cleanHost}`,
    fullName,
  };
}

function parseRepoFullName(value) {
  if (!value) {
    return {};
  }
  const normalized = stripGitRemote(value);
  if (/^\d+$/.test(normalized)) {
    return {
      owner: '',
      repo: normalized,
      fullName: normalized,
      projectId: normalized,
    };
  }
  const parts = normalized.split('/').filter(Boolean);
  if (parts.length < 2) {
    return {};
  }
  return {
    owner: parts.slice(0, -1).join('/'),
    repo: parts[parts.length - 1],
    fullName: parts.join('/'),
  };
}

function normalizeLabelName(label) {
  if (typeof label === 'string') {
    return label;
  }
  if (label && typeof label.name === 'string') {
    return label.name;
  }
  if (label && typeof label.title === 'string') {
    return label.title;
  }
  return '';
}

function normalizeLabels(labels) {
  if (!Array.isArray(labels)) {
    return [];
  }
  return labels.map(normalizeLabelName).filter(Boolean);
}

function normalizeHexColor(value) {
  return String(value || '')
    .trim()
    .replace(/^#/, '')
    .toUpperCase();
}

function providerLabelDefinition(providerName, definition) {
  const color = normalizeHexColor(definition.color);
  return {
    name: definition.name,
    color: providerName === 'gitlab' ? `#${color}` : color,
    description: definition.description || '',
  };
}

function labelMatchesDefinition(providerName, existing, definition) {
  if (!existing) {
    return false;
  }
  const expected = providerLabelDefinition(providerName, definition);
  return (
    normalizeLabelName(existing) === definition.name &&
    normalizeHexColor(existing.color) === normalizeHexColor(expected.color) &&
    String(existing.description || '') === expected.description
  );
}

function planLabelSync(providerName, existing, definition) {
  if (!existing) {
    return 'create';
  }
  if (!labelMatchesDefinition(providerName, existing, definition)) {
    return 'update';
  }
  return 'skip';
}

function normalizeGitlabState(state) {
  if (state === 'opened' || state === 'active') {
    return 'open';
  }
  return typeof state === 'string' ? state : '';
}

function extractTokenFromRemoteUrl(remoteUrl) {
  if (!remoteUrl) {
    return '';
  }
  let parsed;
  try {
    parsed = new URL(String(remoteUrl));
  } catch {
    return '';
  }
  if (!/^https?:$/.test(parsed.protocol) || !parsed.username) {
    return '';
  }

  const password = decodeURIComponent(parsed.password || '');
  if (password) {
    return password.length >= 8 ? password : '';
  }

  const candidate = decodeURIComponent(parsed.username);
  if (candidate === 'git' || candidate.length < 8) {
    return '';
  }

  if (!/^(github_pat_|gh[pousr]_|glpat-|gldt-|glcbt-|glrt-)/.test(candidate)) {
    return '';
  }

  return candidate;
}

function getTokenFromGitRemote() {
  return extractTokenFromRemoteUrl(getGitOriginUrl());
}

function getGithubToken() {
  return process.env.GITHUB_TOKEN || process.env.GH_TOKEN || getTokenFromGitRemote();
}

function githubApiUrl(apiPath) {
  const baseUrl = (process.env.GITHUB_API_URL || 'https://api.github.com').replace(/\/+$/, '');
  return `${baseUrl}${apiPath}`;
}

function githubGraphqlUrl() {
  const baseUrl = (process.env.GITHUB_API_URL || 'https://api.github.com').replace(/\/+$/, '');
  if (baseUrl.endsWith('/api/v3')) {
    return `${baseUrl.slice(0, -'/api/v3'.length)}/api/graphql`;
  }
  return `${baseUrl}/graphql`;
}

async function requestGithub(method, apiPath, body) {
  const token = getGithubToken();
  if (!token) {
    throw new Error('Missing GITHUB_TOKEN or GH_TOKEN for GitHub API access');
  }

  const response = await fetch(githubApiUrl(apiPath), {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  const parsed = parseJsonText(text);
  if (!response.ok) {
    const message =
      parsed && typeof parsed === 'object' && !Array.isArray(parsed) && typeof parsed.message === 'string'
        ? parsed.message
        : `GitHub API HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.response = parsed;
    throw error;
  }

  return parsed;
}

async function requestGithubGraphql(query, variables = {}) {
  const token = getGithubToken();
  if (!token) {
    throw new Error('Missing GITHUB_TOKEN or GH_TOKEN for GitHub API access');
  }

  const response = await fetch(githubGraphqlUrl(), {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, variables }),
  });

  const text = await response.text();
  const parsed = parseJsonText(text);
  if (!response.ok || (parsed && Array.isArray(parsed.errors) && parsed.errors.length > 0)) {
    const message =
      parsed && Array.isArray(parsed.errors) && parsed.errors.length > 0
        ? parsed.errors.map((error) => error.message).filter(Boolean).join('; ')
        : `GitHub GraphQL HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.response = parsed;
    throw error;
  }

  return parsed;
}

async function requestGithubText(method, apiPath, body) {
  const token = getGithubToken();
  if (!token) {
    throw new Error('Missing GITHUB_TOKEN or GH_TOKEN for GitHub API access');
  }

  const response = await fetch(githubApiUrl(apiPath), {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/plain',
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': '2022-11-28',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  if (!response.ok) {
    const error = new Error(text || `GitHub API HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return text;
}

function resolveGithubRepo(payload = {}, options = {}) {
  for (const candidate of [options.repo, process.env.GITHUB_REPOSITORY]) {
    const parsed = parseRepoFullName(candidate);
    if (parsed.owner && parsed.repo) {
      return parsed;
    }
  }

  const repository = payload.repository;
  if (repository && typeof repository.full_name === 'string') {
    const payloadRepo = parseRepoFullName(repository.full_name);
    if (payloadRepo.owner && payloadRepo.repo) {
      return payloadRepo;
    }
  }

  if (repository && repository.owner && typeof repository.owner.login === 'string' && typeof repository.name === 'string') {
    return {
      owner: repository.owner.login,
      repo: repository.name,
      fullName: `${repository.owner.login}/${repository.name}`,
    };
  }

  const remote = parseGitRemoteUrl(process.env.CI_REPOSITORY_URL || getGitOriginUrl());
  if (remote.fullName) {
    const parsedRemote = parseRepoFullName(remote.fullName);
    if (parsedRemote.owner && parsedRemote.repo) {
      return parsedRemote;
    }
  }

  throw new Error('Unable to resolve GitHub repository. Pass --repo or set GITHUB_REPOSITORY.');
}

function githubIssueApiPath(issue) {
  return `/repos/${encodeURIComponent(issue.owner)}/${encodeURIComponent(issue.repo)}/issues/${issue.number}`;
}

function githubRepoApiPath(repo) {
  return `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}`;
}

function normalizeMilestone(milestone = {}) {
  return {
    id: milestone.id ?? milestone.number ?? milestone.iid,
    number: milestone.number ?? milestone.iid ?? milestone.id,
    title: String(milestone.title || '').trim(),
    state: normalizeGitlabState(milestone.state || ''),
    url: milestone.html_url || milestone.web_url || '',
  };
}

async function getGithubDefaultBranch(repo, options = {}) {
  if (options.dryRun) return options.base || process.env.CI_DEFAULT_BRANCH || 'main';
  const current = await requestGithubForApply('GET', githubRepoApiPath(repo));
  return String(current.default_branch || '').trim();
}

async function githubBranchExists(repo, branch, options = {}) {
  if (options.dryRun) return true;
  try {
    await requestGithubForApply('GET', `${githubRepoApiPath(repo)}/branches/${encodeURIComponent(branch)}`);
    return true;
  } catch (error) {
    if (error.status === 404 || /\b404\b|not found/i.test(error.message || '')) return false;
    throw error;
  }
}

async function listGithubMilestones(repo, state = 'open', options = {}) {
  if (options.dryRun) return [];
  const milestones = [];
  for (let page = 1; page <= 10; page += 1) {
    const items = await requestGithubForApply(
      'GET',
      `${githubRepoApiPath(repo)}/milestones?state=${encodeURIComponent(state)}&per_page=100&page=${page}`
    );
    if (!Array.isArray(items) || items.length === 0) break;
    milestones.push(...items.map(normalizeMilestone));
    if (items.length < 100) break;
  }
  return milestones;
}

async function getGithubMilestoneByTitle(repo, title, options = {}) {
  return (await listGithubMilestones(repo, 'all', options)).find((milestone) => milestone.title === title);
}

async function ensureGithubMilestone(repo, title, options = {}) {
  const existing = await getGithubMilestoneByTitle(repo, title, options);
  if (options.dryRun) return { title, state: 'open', action: existing ? 'reopen' : 'create' };
  if (!existing) {
    try {
      const created = await requestGithubForApply('POST', `${githubRepoApiPath(repo)}/milestones`, { title });
      return { ...normalizeMilestone(created), action: 'created' };
    } catch (error) {
      if (error.status !== 422 && !/already exists/i.test(error.message || '')) throw error;
      const concurrent = await getGithubMilestoneByTitle(repo, title, options);
      if (!concurrent) throw error;
      return { ...concurrent, action: 'skipped' };
    }
  }
  if (existing.state === 'closed') {
    const reopened = await requestGithubForApply('PATCH', `${githubRepoApiPath(repo)}/milestones/${existing.number}`, { state: 'open' });
    return { ...normalizeMilestone(reopened), action: 'reopened' };
  }
  return { ...existing, action: 'skipped' };
}

async function closeGithubMilestone(repo, title, options = {}) {
  const existing = await getGithubMilestoneByTitle(repo, title, options);
  if (!existing || existing.state === 'closed') return { title, state: 'closed', action: 'skipped' };
  if (options.dryRun) return { ...existing, state: 'closed', action: 'close' };
  const closed = await requestGithubForApply('PATCH', `${githubRepoApiPath(repo)}/milestones/${existing.number}`, { state: 'closed' });
  return { ...normalizeMilestone(closed), action: 'closed' };
}

async function setGithubIssueMilestone(target, milestone, options = {}) {
  if (options.dryRun) return;
  await requestGithubForApply('PATCH', githubIssueApiPath({ ...target, number: target.issueNumber || target.number }), {
    milestone: milestone ? milestone.number : null,
  });
}

function githubPullRequestApiPath(pr) {
  return `/repos/${encodeURIComponent(pr.owner)}/${encodeURIComponent(pr.repo)}/pulls/${pr.number}`;
}

function githubPullRequestReviewApiPath(pr, reviewId) {
  return `${githubPullRequestApiPath(pr)}/reviews/${encodeURIComponent(reviewId)}`;
}

function githubPullRequestsApiPath(repo) {
  return `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/pulls`;
}

function githubLabelApiPath(repo, label) {
  return `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/labels/${encodeURIComponent(label)}`;
}

function githubIssueCommentApiPath(issue, comment) {
  return `/repos/${encodeURIComponent(issue.owner)}/${encodeURIComponent(issue.repo)}/issues/comments/${comment.id}`;
}

function githubIssueReactionApiPath(issue) {
  return `${githubIssueApiPath(issue)}/reactions`;
}

function githubPullRequestReviewCommentReactionApiPath(pr, comment) {
  return `/repos/${encodeURIComponent(pr.owner)}/${encodeURIComponent(pr.repo)}/pulls/comments/${encodeURIComponent(comment.id)}/reactions`;
}

function buildGithubIssueContext(payload = {}, options = {}) {
  const repo = resolveGithubRepo(payload, options);
  const issue = payload.issue || {};
  const number = options.issueNumber
    ? parsePositiveInteger(options.issueNumber, '--issue-number')
    : parsePositiveInteger(issue.number, 'payload.issue.number');

  return {
    provider: 'github',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    number,
    title: typeof issue.title === 'string' ? issue.title : '',
    body: typeof issue.body === 'string' ? issue.body : '',
    htmlUrl: typeof issue.html_url === 'string' ? issue.html_url : '',
    state: typeof issue.state === 'string' ? issue.state : '',
    author: issue.user && typeof issue.user.login === 'string' ? issue.user.login : '',
    labels: normalizeLabels(issue.labels),
    ...(issue.milestone ? { milestone: normalizeMilestone(issue.milestone) } : {}),
  };
}

function normalizeGithubPullRequest(pr, repo, fallback = {}) {
  const repoFullName = repo.fullName || repo.repoFullName;
  return {
    provider: 'github',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName,
    number: parsePositiveInteger(pr.number || fallback.number, 'pull request number'),
    title: typeof pr.title === 'string' ? pr.title : fallback.title || '',
    body: typeof pr.body === 'string' ? pr.body : fallback.body || '',
    htmlUrl: typeof pr.html_url === 'string' ? pr.html_url : fallback.htmlUrl || '',
    state: typeof pr.state === 'string' ? pr.state : fallback.state || '',
    draft: Boolean(pr.draft),
    merged: Boolean(pr.merged),
    baseRef: pr.base && typeof pr.base.ref === 'string' ? pr.base.ref : fallback.baseRef || '',
    headRef: pr.head && typeof pr.head.ref === 'string' ? pr.head.ref : fallback.headRef || '',
    headSha: pr.head && typeof pr.head.sha === 'string' ? pr.head.sha : fallback.headSha || '',
    labels: normalizeLabels(pr.labels || fallback.labels),
    author: pr.user && typeof pr.user.login === 'string' ? pr.user.login : fallback.author || '',
  };
}

function buildGithubPullRequestContext(payload = {}, options = {}) {
  const repo = resolveGithubRepo(payload, options);
  const pr = payload.pull_request || {};
  const issue = payload.issue || {};
  const number = options.prNumber
    ? parsePositiveInteger(options.prNumber, '--pr-number')
    : parsePositiveInteger(pr.number || issue.number, 'payload.pull_request.number');
  return normalizeGithubPullRequest({ ...pr, number }, repo, {
    number,
    title: issue.title || '',
    body: issue.body || '',
    htmlUrl: issue.html_url || '',
    state: issue.state || '',
    labels: issue.labels || [],
    author: issue.user && issue.user.login,
  });
}

async function fetchCurrentGithubPullRequest(pr, options = {}) {
  if (options.dryRun) {
    return pr;
  }

  const current = await requestGithub('GET', githubPullRequestApiPath(pr));
  return normalizeGithubPullRequest(current, pr, pr);
}

function getGithubCommentContext(payload = {}) {
  const comment = payload.comment || {};
  return {
    id: comment.id,
    author: comment.user && typeof comment.user.login === 'string' ? comment.user.login : '',
    body: typeof comment.body === 'string' ? comment.body : '',
    htmlUrl: typeof comment.html_url === 'string' ? comment.html_url : '',
  };
}

function isGithubReviewCommentCreatedEvent(payload = {}) {
  const hasPullRequestSubject = Boolean(payload.pull_request || (payload.issue && payload.issue.pull_request));
  if (!hasPullRequestSubject || !payload.comment) {
    return {
      ok: false,
      reason: 'not_pull_request_review_comment',
    };
  }
  if (payload.comment.in_reply_to_id !== undefined && payload.comment.in_reply_to_id !== null) {
    return {
      ok: false,
      reason: 'review_comment_reply',
    };
  }
  if (payload.action !== 'created') {
    return {
      ok: false,
      reason: 'unsupported_event_action',
      eventAction: payload.action || '',
    };
  }
  return { ok: true };
}

function getGithubReviewCommentContext(payload = {}) {
  const comment = payload.comment || {};
  const reviewId =
    comment.pull_request_review_id ??
    comment.reviewId ??
    comment.pullRequestReviewId ??
    (payload.review && payload.review.id) ??
    (payload.pull_request_review && payload.pull_request_review.id);
  return {
    id: comment.id === undefined ? '' : String(comment.id),
    reviewId: reviewId === undefined || reviewId === null ? '' : String(reviewId),
    author: comment.user && typeof comment.user.login === 'string' ? comment.user.login : '',
    body: typeof comment.body === 'string' ? comment.body : '',
    htmlUrl: typeof comment.html_url === 'string' ? comment.html_url : '',
    inReplyToId: comment.in_reply_to_id === undefined || comment.in_reply_to_id === null ? '' : String(comment.in_reply_to_id),
    path: typeof comment.path === 'string' ? comment.path : '',
    line: comment.line === undefined ? null : Number(comment.line),
    side: typeof comment.side === 'string' ? comment.side : '',
    diffHunk: typeof comment.diff_hunk === 'string' ? comment.diff_hunk : '',
    createdAt: typeof comment.created_at === 'string' ? comment.created_at : '',
    updatedAt: typeof comment.updated_at === 'string' ? comment.updated_at : '',
    raw: comment,
  };
}

async function fetchCurrentGithubIssue(issue, options = {}) {
  if (options.dryRun || !getGithubToken()) {
    return issue;
  }

  const current = await requestGithub('GET', githubIssueApiPath(issue));
  return {
    ...issue,
    title: typeof current.title === 'string' ? current.title : issue.title,
    body: typeof current.body === 'string' ? current.body : issue.body,
    htmlUrl: typeof current.html_url === 'string' ? current.html_url : issue.htmlUrl,
    state: typeof current.state === 'string' ? current.state : issue.state,
    author: current.user && typeof current.user.login === 'string' ? current.user.login : issue.author,
    labels: normalizeLabels(current.labels),
    milestone: current.milestone ? normalizeMilestone(current.milestone) : null,
  };
}

async function listGithubIssueComments(issue, options = {}) {
  if (options.dryRun) {
    return [];
  }

  const comments = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGithub('GET', `${githubIssueApiPath(issue)}/comments?per_page=100&page=${page}`);
    if (!Array.isArray(pageItems) || pageItems.length === 0) {
      break;
    }
    comments.push(...pageItems);
    if (pageItems.length < 100) {
      break;
    }
  }
  return comments;
}

async function listGithubPullRequestReviewComments(pr, options = {}) {
  if (options.dryRun) {
    return [];
  }

  const comments = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGithub('GET', `${githubPullRequestApiPath(pr)}/comments?per_page=100&page=${page}`);
    if (!Array.isArray(pageItems) || pageItems.length === 0) {
      break;
    }
    comments.push(...pageItems);
    if (pageItems.length < 100) {
      break;
    }
  }
  return comments;
}

function normalizeGithubReviewComment(comment) {
  const normalized = {
    path: comment.path,
    body: comment.body,
  };
  if (comment.position !== undefined) {
    normalized.position = Number(comment.position);
    return normalized;
  }
  if (comment.line !== undefined) {
    normalized.line = Number(comment.line);
  }
  if (comment.side) {
    normalized.side = String(comment.side).toUpperCase();
  } else if (comment.line !== undefined) {
    normalized.side = 'RIGHT';
  }
  if (comment.startLine !== undefined || comment.start_line !== undefined) {
    normalized.start_line = Number(comment.startLine ?? comment.start_line);
  }
  if (comment.startSide || comment.start_side) {
    normalized.start_side = String(comment.startSide || comment.start_side).toUpperCase();
  }
  return normalized;
}

async function createGithubIssueComment(issue, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, commentIssue: issue.number, body }, null, 2));
    return { id: 'dry-run-comment', html_url: '' };
  }

  return requestGithub('POST', `${githubIssueApiPath(issue)}/comments`, { body });
}

async function submitGithubPullRequestReview(pr, body, options = {}, comments = []) {
  const requestBody = {
    body,
    event: 'COMMENT',
  };
  if (options.commitId || pr.headSha) {
    requestBody.commit_id = options.commitId || pr.headSha;
  }
  if (comments.length > 0) {
    requestBody.comments = comments.map(normalizeGithubReviewComment);
  }

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reviewPullRequest: pr.number, body: requestBody }, null, 2));
    return { id: 'dry-run-review', html_url: '' };
  }

  return requestGithub('POST', `${githubPullRequestApiPath(pr)}/reviews`, requestBody);
}

async function updateGithubIssueComment(issue, commentId, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, updateComment: commentId, body }, null, 2));
    return;
  }

  await requestGithub(
    'PATCH',
    `/repos/${encodeURIComponent(issue.owner)}/${encodeURIComponent(issue.repo)}/issues/comments/${commentId}`,
    { body }
  );
}

async function deleteGithubIssueComment(issue, commentId, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, deleteComment: commentId }, null, 2));
    return;
  }

  await requestGithub(
    'DELETE',
    `/repos/${encodeURIComponent(issue.owner)}/${encodeURIComponent(issue.repo)}/issues/comments/${commentId}`
  );
}

async function addGithubTriggerCommentReaction(issue, comment, content, options = {}) {
  if (!comment.id) {
    console.warn('Trigger comment has no id; eyes reaction skipped.');
    return;
  }

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionComment: comment.id, content }, null, 2));
    return;
  }

  await requestGithub('POST', `${githubIssueCommentApiPath(issue, comment)}/reactions`, { content });
}

function isGithubInlineReviewComment(comment = {}) {
  return Boolean(
    comment.reviewId ||
    comment.pullRequestReviewId ||
    comment.pull_request_review_id ||
    comment.path ||
    comment.diffHunk ||
    comment.diff_hunk ||
    String(comment.htmlUrl || comment.html_url || '').includes('#discussion_r')
  );
}

function isDuplicateReactionError(error) {
  return Boolean(
    error &&
    (
      (error.status === 422 && /already exists/i.test(error.message || '')) ||
      /already (?:exists|reacted)/i.test(error.message || '')
    )
  );
}

function githubGraphqlReactionContent(content) {
  const normalized = String(content || '').trim().toLowerCase();
  const map = {
    '+1': 'THUMBS_UP',
    '-1': 'THUMBS_DOWN',
    laugh: 'LAUGH',
    confused: 'CONFUSED',
    heart: 'HEART',
    hooray: 'HOORAY',
    rocket: 'ROCKET',
    eyes: 'EYES',
  };
  return map[normalized] || 'EYES';
}

async function addGithubPullRequestReviewReaction(pr, reviewId, content) {
  if (!reviewId) {
    return false;
  }
  const review = await requestGithub('GET', githubPullRequestReviewApiPath(pr, reviewId));
  const subjectId = review && review.node_id;
  if (!subjectId) {
    return false;
  }

  await requestGithubGraphql(
    `mutation AddReaction($subjectId: ID!, $content: ReactionContent!) {
      addReaction(input: {subjectId: $subjectId, content: $content}) {
        reaction { content }
      }
    }`,
    {
      subjectId,
      content: githubGraphqlReactionContent(content),
    }
  );
  return true;
}

async function addGithubReviewCommentReaction(pr, comment, content, options = {}) {
  if (!comment.id) {
    console.warn('Review comment has no id; eyes reaction skipped.');
    return;
  }

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionReviewComment: comment.id, content }, null, 2));
    return;
  }

  try {
    if (comment.reviewId) {
      const reactedToReview = await addGithubPullRequestReviewReaction(pr, comment.reviewId, content);
      if (reactedToReview) {
        return;
      }
    }
  } catch (error) {
    if (!isDuplicateReactionError(error)) {
      console.warn(`Unable to add reaction to pull request review; falling back to comment reaction. ${error instanceof Error ? error.message : String(error)}`);
    } else {
      return;
    }
  }

  const apiPath = isGithubInlineReviewComment(comment)
    ? githubPullRequestReviewCommentReactionApiPath(pr, comment)
    : `${githubIssueCommentApiPath(pr, comment)}/reactions`;
  try {
    await requestGithub('POST', apiPath, { content });
  } catch (error) {
    if (!isDuplicateReactionError(error)) {
      throw error;
    }
  }
}

async function addGithubIssueReaction(issue, content, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionIssue: issue.number, content }, null, 2));
    return;
  }

  await requestGithub('POST', githubIssueReactionApiPath(issue), { content });
}

async function collectGithubWorkflowRunFailureDetails(repo, runId, options = {}) {
  if (options.dryRun || !runId || !getGithubToken()) {
    return { jobs: [] };
  }

  const jobsPath = `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/actions/runs/${encodeURIComponent(runId)}/jobs?filter=latest&per_page=100`;
  const response = await requestGithub('GET', jobsPath);
  const jobs = Array.isArray(response && response.jobs) ? response.jobs : [];
  const failedJobs = jobs.filter((job) => ['failure', 'timed_out', 'cancelled'].includes(String(job.conclusion || '').toLowerCase()));
  const enriched = [];

  for (const job of failedJobs.slice(0, 3)) {
    let log = '';
    if (job.id) {
      try {
        log = await requestGithubText(
          'GET',
          `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/actions/jobs/${encodeURIComponent(job.id)}/logs`
        );
      } catch {
        log = '';
      }
    }
    enriched.push({
      id: job.id,
      name: job.name || '',
      htmlUrl: job.html_url || '',
      conclusion: job.conclusion || '',
      steps: Array.isArray(job.steps) ? job.steps : [],
      log,
    });
  }

  return { jobs: enriched };
}

function getGitlabToken(options = {}) {
  return (
    options.gitlabToken ||
    process.env.ISSUE_FLOW_GITLAB_TOKEN ||
    process.env.GITLAB_TOKEN ||
    process.env.GL_TOKEN ||
    process.env.GITLAB_PRIVATE_TOKEN ||
    process.env.CI_JOB_TOKEN ||
    getTokenFromGitRemote()
  );
}

function hasGlab() {
  const result = spawnSync('glab', ['--version'], {
    encoding: 'utf8',
  });
  return !result.error && result.status === 0;
}

function gitlabApiBaseUrl(options = {}) {
  const explicitApiUrl = options.gitlabApiUrl || process.env.GITLAB_API_URL;
  if (explicitApiUrl) {
    return explicitApiUrl.replace(/\/+$/, '');
  }
  const baseUrl =
    options.gitlabUrl ||
    process.env.GITLAB_BASE_URL ||
    process.env.CI_SERVER_URL ||
    gitRemoteBaseUrl(options) ||
    'https://gitlab.com';
  return `${baseUrl.replace(/\/+$/, '')}/api/v4`;
}

function gitRemoteBaseUrl(options = {}) {
  const remote = parseGitRemoteUrl(options.repo || process.env.CI_REPOSITORY_URL || getGitOriginUrl());
  return remote.baseUrl || '';
}

function gitlabHostname(options = {}) {
  const candidate =
    options.gitlabUrl ||
    process.env.GITLAB_BASE_URL ||
    process.env.CI_SERVER_URL ||
    options.gitlabApiUrl ||
    process.env.GITLAB_API_URL ||
    gitRemoteBaseUrl(options);
  if (!candidate) {
    return '';
  }
  try {
    return new URL(candidate).host;
  } catch {
    return '';
  }
}

function gitlabApiBodyArgs(body) {
  if (body === undefined) {
    return [];
  }
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new Error('GitLab glab fallback body must be an object');
  }

  const args = [];
  for (const [key, value] of Object.entries(body)) {
    if (value === undefined) {
      continue;
    }
    if (typeof value === 'string') {
      args.push('--raw-field', `${key}=${value}`);
      continue;
    }
    if (value === null || typeof value === 'boolean' || typeof value === 'number') {
      args.push('--field', `${key}=${value === null ? 'null' : String(value)}`);
      continue;
    }
    throw new Error(`GitLab glab fallback does not support non-scalar body field: ${key}`);
  }
  return args;
}

function requestGitlabWithGlab(method, apiPath, body, options = {}) {
  const args = ['api', apiPath.replace(/^\//, ''), '--method', method];
  const hostname = gitlabHostname(options);

  if (hostname && hostname !== 'gitlab.com') {
    args.push('--hostname', hostname);
  }

  args.push(...gitlabApiBodyArgs(body));

  const result = spawnSync('glab', args, {
    encoding: 'utf8',
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(result.stderr.trim() || `glab api exited with status ${result.status ?? 1}`);
  }
  return parseJsonText(result.stdout.trim());
}

async function requestGitlab(method, apiPath, body, options = {}) {
  const token = getGitlabToken(options);
  if (!token) {
    return requestGitlabWithGlab(method, apiPath, body, options);
  }

  const url = new URL(`${gitlabApiBaseUrl(options)}${apiPath}`);
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  };
  const tokenAuth = String(options.gitlabTokenAuth || process.env.GITLAB_TOKEN_AUTH || '').toLowerCase();
  if (tokenAuth === 'bearer') {
    headers.Authorization = `Bearer ${token}`;
  } else if (process.env.CI_JOB_TOKEN && token === process.env.CI_JOB_TOKEN) {
    headers['JOB-TOKEN'] = token;
  } else {
    headers['PRIVATE-TOKEN'] = token;
  }

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  const parsed = parseJsonText(text);
  if (!response.ok) {
    const message =
      parsed && typeof parsed === 'object' && !Array.isArray(parsed) && parsed.message
        ? typeof parsed.message === 'string'
          ? parsed.message
          : JSON.stringify(parsed.message)
        : `GitLab API HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.response = parsed;
    throw error;
  }

  return parsed;
}

function resolveGitlabRepo(payload = {}, options = {}) {
  for (const candidate of [
    options.repo,
    options.gitlabProject,
    process.env.GITLAB_PROJECT_PATH,
    process.env.CI_PROJECT_PATH,
    process.env.GITLAB_BRIDGE_REPOSITORY,
    process.env.AGENTRIX_REPOSITORY_OWNER && process.env.AGENTRIX_REPOSITORY_NAME
      ? `${process.env.AGENTRIX_REPOSITORY_OWNER}/${process.env.AGENTRIX_REPOSITORY_NAME}`
      : '',
  ]) {
    const parsed = parseRepoFullName(candidate);
    if (parsed.fullName) {
      if (!parsed.projectId && process.env.CI_PROJECT_ID && parsed.fullName === process.env.CI_PROJECT_PATH) {
        parsed.projectId = process.env.CI_PROJECT_ID;
      }
      return parsed;
    }
  }

  const project = payload.project || {};
  const parsedProject = parseRepoFullName(project.path_with_namespace || project.web_url || project.git_http_url);
  if (parsedProject.fullName) {
    if (project.id !== undefined) {
      parsedProject.projectId = String(project.id);
    }
    return parsedProject;
  }

  if (process.env.CI_PROJECT_ID) {
    return {
      owner: '',
      repo: process.env.CI_PROJECT_ID,
      fullName: process.env.CI_PROJECT_PATH || process.env.CI_PROJECT_ID,
      projectId: process.env.CI_PROJECT_ID,
    };
  }

  const remote = parseGitRemoteUrl(process.env.CI_REPOSITORY_URL || getGitOriginUrl());
  if (remote.fullName) {
    const parsedRemote = parseRepoFullName(remote.fullName);
    if (parsedRemote.fullName) {
      return parsedRemote;
    }
  }

  throw new Error('Unable to resolve GitLab project. Pass --repo, set GITLAB_PROJECT_PATH, or set CI_PROJECT_PATH.');
}

function gitlabProjectRef(issueOrRepo) {
  const project = issueOrRepo.projectId || issueOrRepo.repoFullName || issueOrRepo.fullName;
  return encodeURIComponent(project);
}

function gitlabIssueApiPath(issue) {
  return `/projects/${gitlabProjectRef(issue)}/issues/${encodeURIComponent(issue.number)}`;
}

function gitlabProjectApiPath(repo) {
  return `/projects/${gitlabProjectRef(repo)}`;
}

async function getGitlabDefaultBranch(repo, options = {}) {
  if (options.dryRun) return options.base || process.env.CI_DEFAULT_BRANCH || 'main';
  const current = await requestGitlab('GET', gitlabProjectApiPath(repo), undefined, options);
  return String(current.default_branch || '').trim();
}

async function gitlabBranchExists(repo, branch, options = {}) {
  if (options.dryRun) return true;
  try {
    await requestGitlab('GET', `${gitlabProjectApiPath(repo)}/repository/branches/${encodeURIComponent(branch)}`, undefined, options);
    return true;
  } catch (error) {
    if (error.status === 404 || /\b404\b|not found/i.test(error.message || '')) return false;
    throw error;
  }
}

async function listGitlabMilestones(repo, state = 'open', options = {}) {
  if (options.dryRun) return [];
  const milestones = [];
  const gitlabState = state === 'open' ? 'active' : state === 'closed' ? 'closed' : '';
  for (let page = 1; page <= 10; page += 1) {
    const query = new URLSearchParams({ per_page: '100', page: String(page) });
    if (gitlabState) query.set('state', gitlabState);
    const items = await requestGitlab('GET', `${gitlabProjectApiPath(repo)}/milestones?${query}`, undefined, options);
    if (!Array.isArray(items) || items.length === 0) break;
    milestones.push(...items.map(normalizeMilestone));
    if (items.length < 100) break;
  }
  return milestones;
}

async function getGitlabMilestoneByTitle(repo, title, options = {}) {
  return (await listGitlabMilestones(repo, 'all', options)).find((milestone) => milestone.title === title);
}

async function ensureGitlabMilestone(repo, title, options = {}) {
  const existing = await getGitlabMilestoneByTitle(repo, title, options);
  if (options.dryRun) return { title, state: 'open', action: existing ? 'reopen' : 'create' };
  if (!existing) {
    try {
      const created = await requestGitlab('POST', `${gitlabProjectApiPath(repo)}/milestones`, { title }, options);
      return { ...normalizeMilestone(created), action: 'created' };
    } catch (error) {
      if (![400, 409].includes(error.status) && !/already (?:been )?taken|already exists/i.test(error.message || '')) throw error;
      const concurrent = await getGitlabMilestoneByTitle(repo, title, options);
      if (!concurrent) throw error;
      return { ...concurrent, action: 'skipped' };
    }
  }
  if (existing.state === 'closed') {
    const reopened = await requestGitlab('PUT', `${gitlabProjectApiPath(repo)}/milestones/${existing.id}`, { state_event: 'activate' }, options);
    return { ...normalizeMilestone(reopened), action: 'reopened' };
  }
  return { ...existing, action: 'skipped' };
}

async function closeGitlabMilestone(repo, title, options = {}) {
  const existing = await getGitlabMilestoneByTitle(repo, title, options);
  if (!existing || existing.state === 'closed') return { title, state: 'closed', action: 'skipped' };
  if (options.dryRun) return { ...existing, state: 'closed', action: 'close' };
  const closed = await requestGitlab('PUT', `${gitlabProjectApiPath(repo)}/milestones/${existing.id}`, { state_event: 'close' }, options);
  return { ...normalizeMilestone(closed), action: 'closed' };
}

async function setGitlabIssueMilestone(target, milestone, options = {}) {
  if (options.dryRun) return;
  await requestGitlab('PUT', gitlabIssueApiPath(target), { milestone_id: milestone ? milestone.id : null }, options);
}

function gitlabIssueNotesApiPath(issue) {
  return `${gitlabIssueApiPath(issue)}/notes`;
}

function pickGitlabIssuePayload(payload = {}) {
  if (payload.issue) {
    return payload.issue;
  }
  if (payload.object_kind === 'issue') {
    return payload.object_attributes || {};
  }
  return {};
}

function buildGitlabIssueContext(payload = {}, options = {}) {
  const repo = resolveGitlabRepo(payload, options);
  const issue = pickGitlabIssuePayload(payload);
  const number = options.issueNumber
    ? parsePositiveInteger(options.issueNumber, '--issue-number')
    : parsePositiveInteger(issue.iid || issue.number, 'GitLab issue iid');

  return {
    provider: 'gitlab',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    projectId: repo.projectId,
    number,
    title: typeof issue.title === 'string' ? issue.title : '',
    body: typeof issue.description === 'string' ? issue.description : typeof issue.body === 'string' ? issue.body : '',
    htmlUrl: typeof issue.url === 'string' ? issue.url : typeof issue.web_url === 'string' ? issue.web_url : '',
    state: normalizeGitlabState(issue.state),
    author:
      (issue.author && (issue.author.username || issue.author.name)) ||
      (payload.user && (payload.user.username || payload.user.name)) ||
      '',
    labels: normalizeLabels(issue.labels || payload.labels),
    ...(issue.milestone ? { milestone: normalizeMilestone(issue.milestone) } : {}),
  };
}

function isGitlabMergeRequestOrNonIssue(payload = {}) {
  if (payload.object_kind === 'merge_request') {
    return true;
  }
  if (payload.object_kind === 'note') {
    const noteableType = payload.object_attributes && payload.object_attributes.noteable_type;
    return noteableType !== 'Issue';
  }
  return false;
}

function isGitlabBotComment(payload = {}) {
  const user = payload.user || {};
  return user.bot === true || user.user_type === 'bot';
}

function getGitlabCommentContext(payload = {}) {
  const note = payload.object_attributes || {};
  return {
    id: note.id,
    author: payload.user && (payload.user.username || payload.user.name) ? payload.user.username || payload.user.name : '',
    body: typeof note.note === 'string' ? note.note : '',
    htmlUrl: typeof note.url === 'string' ? note.url : '',
  };
}

async function fetchCurrentGitlabIssue(issue, options = {}) {
  if (options.dryRun || !getGitlabToken(options)) {
    return issue;
  }

  const current = await requestGitlab('GET', gitlabIssueApiPath(issue), undefined, options);
  return {
    ...issue,
    title: typeof current.title === 'string' ? current.title : issue.title,
    body: typeof current.description === 'string' ? current.description : issue.body,
    htmlUrl: typeof current.web_url === 'string' ? current.web_url : issue.htmlUrl,
    state: normalizeGitlabState(current.state),
    author:
      current.author && (current.author.username || current.author.name)
        ? current.author.username || current.author.name
        : issue.author,
    labels: normalizeLabels(current.labels),
    milestone: current.milestone ? normalizeMilestone(current.milestone) : null,
  };
}

async function listGitlabIssueComments(issue, options = {}) {
  if (options.dryRun) {
    return [];
  }

  const comments = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGitlab(
      'GET',
      `${gitlabIssueNotesApiPath(issue)}?per_page=100&page=${page}`,
      undefined,
      options
    );
    if (!Array.isArray(pageItems) || pageItems.length === 0) {
      break;
    }
    comments.push(...pageItems);
    if (pageItems.length < 100) {
      break;
    }
  }
  return comments;
}

async function createGitlabIssueComment(issue, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, commentIssue: issue.number, body }, null, 2));
    return { id: 'dry-run-comment', web_url: '' };
  }

  return requestGitlab('POST', gitlabIssueNotesApiPath(issue), { body }, options);
}

async function updateGitlabIssueComment(issue, commentId, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, updateComment: commentId, body }, null, 2));
    return;
  }

  await requestGitlab('PUT', `${gitlabIssueNotesApiPath(issue)}/${encodeURIComponent(commentId)}`, { body }, options);
}

async function deleteGitlabIssueComment(issue, commentId, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, deleteComment: commentId }, null, 2));
    return;
  }

  await requestGitlab('DELETE', `${gitlabIssueNotesApiPath(issue)}/${encodeURIComponent(commentId)}`, undefined, options);
}

async function addGitlabTriggerCommentReaction(issue, comment, content, options = {}) {
  if (!comment.id) {
    console.warn('Trigger comment has no id; eyes reaction skipped.');
    return;
  }

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionComment: comment.id, content }, null, 2));
    return;
  }

  await requestGitlab(
    'POST',
    `${gitlabIssueNotesApiPath(issue)}/${encodeURIComponent(comment.id)}/award_emoji`,
    { name: content },
    options
  );
}

async function addGitlabReviewCommentReaction(pr, comment, content, options = {}) {
  if (!comment.id) {
    console.warn('Review comment has no id; eyes reaction skipped.');
    return;
  }

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionReviewComment: comment.id, content }, null, 2));
    return;
  }

  await requestGitlab(
    'POST',
    `${gitlabMergeRequestNotesApiPath(pr)}/${encodeURIComponent(comment.id)}/award_emoji`,
    { name: content },
    options
  );
}

async function addGitlabIssueReaction(issue, content, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reactionIssue: issue.number, content }, null, 2));
    return;
  }

  await requestGitlab('POST', `${gitlabIssueApiPath(issue)}/award_emoji`, { name: content }, options);
}

function gitlabMergeRequestsApiPath(repo) {
  return `/projects/${gitlabProjectRef(repo)}/merge_requests`;
}

function gitlabMergeRequestApiPath(pr) {
  return `${gitlabMergeRequestsApiPath(pr)}/${encodeURIComponent(pr.number)}`;
}

function gitlabMergeRequestNotesApiPath(pr) {
  return `${gitlabMergeRequestApiPath(pr)}/notes`;
}

function gitlabMergeRequestDiscussionsApiPath(pr) {
  return `${gitlabMergeRequestApiPath(pr)}/discussions`;
}

function pickGitlabMergeRequestPayload(payload = {}) {
  if (payload.merge_request) {
    return payload.merge_request;
  }
  if (payload.object_kind === 'note' && payload.merge_request) {
    return payload.merge_request;
  }
  if (payload.object_kind === 'merge_request') {
    return payload.object_attributes || {};
  }
  return {};
}

function normalizeGitlabPullRequest(pr, repo, fallback = {}) {
  const labels = pr.labels || fallback.labels;
  const state = normalizeGitlabState(pr.state || fallback.state);
  const diffRefs = pr.diff_refs || {};
  const lastCommit = pr.last_commit || {};
  const repoFullName = repo.fullName || repo.repoFullName;
  const draft =
    pr.work_in_progress === true ||
    pr.draft === true ||
    /^draft:/i.test(pr.title || fallback.title || '');
  return {
    provider: 'gitlab',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName,
    projectId: repo.projectId,
    number: parsePositiveInteger(pr.iid || pr.number || fallback.number, 'merge request iid'),
    title: typeof pr.title === 'string' ? pr.title : fallback.title || '',
    body: typeof pr.description === 'string' ? pr.description : typeof pr.body === 'string' ? pr.body : fallback.body || '',
    htmlUrl: typeof pr.web_url === 'string' ? pr.web_url : typeof pr.url === 'string' ? pr.url : fallback.htmlUrl || '',
    state,
    draft,
    merged: state === 'merged' || pr.merged === true || fallback.merged === true,
    baseRef: typeof pr.target_branch === 'string' ? pr.target_branch : fallback.baseRef || '',
    headRef: typeof pr.source_branch === 'string' ? pr.source_branch : fallback.headRef || '',
    headSha: pr.sha || diffRefs.head_sha || lastCommit.id || fallback.headSha || '',
    labels: normalizeLabels(labels),
    author:
      (pr.author && (pr.author.username || pr.author.name)) ||
      (fallback.author || ''),
  };
}

function buildGitlabPullRequestContext(payload = {}, options = {}) {
  const repo = resolveGitlabRepo(payload, options);
  const pr = pickGitlabMergeRequestPayload(payload);
  const number = options.prNumber
    ? parsePositiveInteger(options.prNumber, '--pr-number')
    : parsePositiveInteger(pr.iid || pr.number, 'GitLab merge request iid');
  return normalizeGitlabPullRequest({ ...pr, iid: number }, repo);
}

function isGitlabReviewCommentCreatedEvent(payload = {}) {
  if (payload.object_kind !== 'note' && !payload.object_attributes) {
    return {
      ok: false,
      reason: 'not_pull_request_review_comment',
    };
  }
  const note = payload.object_attributes || {};
  const noteableType = String(note.noteable_type || '');
  if (noteableType && noteableType !== 'MergeRequest') {
    return {
      ok: false,
      reason: 'not_pull_request_review_comment',
    };
  }
  if (!payload.merge_request && !payload.object_attributes.merge_request && !note.noteable_id && !note.noteable_iid) {
    return {
      ok: false,
      reason: 'not_pull_request_review_comment',
    };
  }
  const action = String(note.action || payload.action || process.env.GITLAB_BRIDGE_EVENT_ACTION || 'create').toLowerCase();
  if (action && action !== 'create' && action !== 'created') {
    return {
      ok: false,
      reason: 'unsupported_event_action',
      eventAction: action,
    };
  }
  return { ok: true };
}

function getGitlabReviewCommentContext(payload = {}) {
  const note = payload.object_attributes || {};
  const position = note.position || payload.position || {};
  const reviewId =
    note.pull_request_review_id ??
    note.reviewId ??
    note.pullRequestReviewId ??
    payload.pull_request_review_id ??
    payload.reviewId ??
    payload.pullRequestReviewId ??
    (payload.review && payload.review.id);
  return {
    id: note.id === undefined ? '' : String(note.id),
    reviewId: reviewId === undefined || reviewId === null ? '' : String(reviewId),
    discussionId: String(note.discussion_id || note.discussionId || payload.discussion_id || payload.discussionId || ''),
    author: payload.user && (payload.user.username || payload.user.name) ? payload.user.username || payload.user.name : '',
    body: typeof note.note === 'string' ? note.note : typeof note.body === 'string' ? note.body : '',
    htmlUrl: typeof note.url === 'string' ? note.url : typeof note.web_url === 'string' ? note.web_url : '',
    path: String(position.new_path || position.old_path || note.path || ''),
    line: position.new_line === undefined && position.old_line === undefined ? null : Number(position.new_line || position.old_line),
    side: position.old_line ? 'LEFT' : position.new_line ? 'RIGHT' : '',
    createdAt: typeof note.created_at === 'string' ? note.created_at : '',
    updatedAt: typeof note.updated_at === 'string' ? note.updated_at : '',
    raw: note,
  };
}

async function fetchCurrentGitlabPullRequest(pr, options = {}) {
  if (options.dryRun) {
    return pr;
  }

  const current = await requestGitlab('GET', gitlabMergeRequestApiPath(pr), undefined, options);
  return normalizeGitlabPullRequest(current, pr, pr);
}

async function listGitlabPullRequestComments(pr, options = {}) {
  if (options.dryRun) {
    return [];
  }

  const comments = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGitlab(
      'GET',
      `${gitlabMergeRequestNotesApiPath(pr)}?per_page=100&page=${page}`,
      undefined,
      options
    );
    if (!Array.isArray(pageItems) || pageItems.length === 0) {
      break;
    }
    comments.push(...pageItems);
    if (pageItems.length < 100) {
      break;
    }
  }
  return comments;
}

async function listGitlabPullRequestReviewComments(pr, options = {}) {
  if (options.dryRun) {
    return [];
  }

  const comments = [];
  for (let page = 1; page <= 10; page += 1) {
    const discussions = await requestGitlab(
      'GET',
      `${gitlabMergeRequestDiscussionsApiPath(pr)}?per_page=100&page=${page}`,
      undefined,
      options
    );
    if (!Array.isArray(discussions) || discussions.length === 0) {
      break;
    }
    for (const discussion of discussions) {
      const notes = Array.isArray(discussion.notes) ? discussion.notes : [];
      if (!notes.some((note) => note && note.position)) {
        continue;
      }
      for (const note of notes) {
        if (note && note.system) {
          continue;
        }
        comments.push({
          ...note,
          discussionId: discussion.id,
          individualNote: Boolean(discussion.individual_note),
        });
      }
    }
    if (discussions.length < 100) {
      break;
    }
  }
  return comments;
}

async function fetchGitlabMergeRequestVersion(pr, options = {}) {
  const versions = await requestGitlab('GET', `${gitlabMergeRequestApiPath(pr)}/versions?per_page=1`, undefined, options);
  const version = Array.isArray(versions) ? versions[0] : undefined;
  if (!version) {
    throw new Error('Unable to resolve GitLab merge request diff version for inline review comments');
  }
  return version;
}

function normalizeGitlabReviewCommentPosition(comment, version) {
  if (comment.position && typeof comment.position === 'object' && !Array.isArray(comment.position)) {
    return comment.position;
  }
  const side = String(comment.side || 'RIGHT').toUpperCase();
  const line = Number(comment.line ?? comment.newLine ?? comment.new_line ?? comment.oldLine ?? comment.old_line);
  if (!Number.isFinite(line) || line <= 0) {
    throw new Error(`GitLab inline review comment for ${comment.path} must include a positive line`);
  }
  const path = comment.path;
  const position = {
    base_sha: version.base_commit_sha,
    start_sha: version.start_commit_sha,
    head_sha: version.head_commit_sha,
    old_path: comment.oldPath || comment.old_path || path,
    new_path: comment.newPath || comment.new_path || path,
    position_type: comment.positionType || comment.position_type || 'text',
  };
  if (side === 'LEFT') {
    position.old_line = line;
  } else {
    position.new_line = line;
  }
  return position;
}

async function createGitlabPullRequestComment(pr, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, commentPullRequest: pr.number, body }, null, 2));
    return { id: 'dry-run-comment', web_url: '' };
  }

  return requestGitlab('POST', gitlabMergeRequestNotesApiPath(pr), { body }, options);
}

async function submitGitlabPullRequestReview(pr, body, options = {}, comments = []) {
  const note = await createGitlabPullRequestComment(pr, body, options);
  let discussionUrl = '';
  if (comments.length > 0 && !options.dryRun) {
    const version = await fetchGitlabMergeRequestVersion(pr, options);
    for (const comment of comments) {
      const discussion = await requestGitlab(
        'POST',
        gitlabMergeRequestDiscussionsApiPath(pr),
        {
          body: comment.body,
          position: normalizeGitlabReviewCommentPosition(comment, version),
        },
        options
      );
      if (!discussionUrl) {
        const firstNote = discussion && Array.isArray(discussion.notes) ? discussion.notes[0] : undefined;
        discussionUrl = firstNote && firstNote.web_url ? firstNote.web_url : '';
      }
    }
  }
  if (comments.length > 0 && options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, reviewPullRequest: pr.number, inlineComments: comments }, null, 2));
  }
  return discussionUrl ? { ...note, web_url: note.web_url || discussionUrl } : note;
}

async function updateGitlabPullRequestComment(pr, commentId, body, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, updateComment: commentId, body }, null, 2));
    return;
  }

  await requestGitlab('PUT', `${gitlabMergeRequestNotesApiPath(pr)}/${encodeURIComponent(commentId)}`, { body }, options);
}

async function deleteGitlabPullRequestComment(pr, commentId, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, deleteComment: commentId }, null, 2));
    return;
  }

  await requestGitlab('DELETE', `${gitlabMergeRequestNotesApiPath(pr)}/${encodeURIComponent(commentId)}`, undefined, options);
}

function readBodyFile(bodyFile) {
  return fs.readFileSync(bodyFile, 'utf8');
}

function runProviderOutput(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    cwd: options.cwd,
  });
  if (result.error) {
    if (options.optional) {
      return '';
    }
    throw result.error;
  }
  if (result.status !== 0) {
    if (options.optional) {
      return '';
    }
    throw new Error(result.stderr.trim() || `${command} ${args.join(' ')} exited with status ${result.status ?? 1}`);
  }
  return result.stdout.trim();
}

function runProviderChecked(command, args, options = {}) {
  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, command, args }, null, 2));
    return '';
  }

  const result = spawnSync(command, args, {
    encoding: 'utf8',
    stdio: options.inherit ? 'inherit' : ['ignore', 'pipe', 'pipe'],
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(result.stderr.trim() || `${command} ${args.join(' ')} exited with status ${result.status ?? 1}`);
  }
  return result.stdout ? result.stdout.trim() : '';
}

function uniqueNonEmpty(values) {
  return [...new Set(values.map((value) => String(value || '').trim()).filter(Boolean))];
}

function headBranchFilterCandidates(repo, headBranch) {
  const branch = String(headBranch || '').trim();
  if (!branch) {
    return [];
  }
  if (branch.includes(':')) {
    return [branch];
  }
  return uniqueNonEmpty([branch, `${repo.owner}:${branch}`]);
}

function existingPullRequestApiHead(repo, headBranch) {
  const branch = String(headBranch || '').trim();
  if (!branch || branch.includes(':')) {
    return branch;
  }
  return `${repo.owner}:${branch}`;
}

function normalizeOptionalUrl(value) {
  const trimmed = String(value || '').trim();
  return trimmed === 'null' ? '' : trimmed;
}

function isExistingPullRequestError(error) {
  const message = error instanceof Error ? error.message : String(error);
  if (/pull request .*already exists/i.test(message) || /already exists for [^\s]+:[^\s]+/i.test(message)) {
    return true;
  }

  const response = error && typeof error === 'object' ? error.response : undefined;
  if (!response) {
    return false;
  }
  return /pull request .*already exists/i.test(JSON.stringify(response));
}

async function ensureGithubPullRequestLabel(repo, label, config = {}, options = {}) {
  const labelColor = config.color || config.labelColor || '1D76DB';
  const labelDescription = config.description || config.labelDescription || '';

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, ensureLabel: label, repo: repo.fullName }, null, 2));
    return;
  }

  if (!getGithubToken()) {
    const existing = runProviderOutput(
      'gh',
      ['label', 'list', '--repo', repo.fullName, '--search', label, '--json', 'name', '--jq', '.[].name']
    )
      .split('\n')
      .map((name) => name.trim())
      .filter(Boolean);
    if (existing.includes(label)) {
      return;
    }

    try {
      runProviderChecked('gh', [
        'label',
        'create',
        label,
        '--repo',
        repo.fullName,
        '--color',
        labelColor,
        '--description',
        labelDescription,
      ]);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!/already exists/i.test(message)) {
        throw error;
      }
    }
    return;
  }

  try {
    await requestGithub('GET', githubLabelApiPath(repo, label));
    return;
  } catch (error) {
    if (!error || error.status !== 404) {
      throw error;
    }
  }

  try {
    await requestGithub('POST', `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/labels`, {
      name: label,
      color: labelColor,
      description: labelDescription,
    });
  } catch (error) {
    if (!error || error.status !== 422 || !/already exists/i.test(error.message || '')) {
      throw error;
    }
  }
}

async function findExistingGithubPullRequest(repo, headBranch, options = {}) {
  if (options.dryRun) {
    return undefined;
  }

  if (!getGithubToken()) {
    for (const candidate of headBranchFilterCandidates(repo, headBranch)) {
      const url = normalizeOptionalUrl(
        runProviderOutput(
          'gh',
          [
            'pr',
            'list',
            '--repo',
            repo.fullName,
            '--head',
            candidate,
            '--state',
            'open',
            '--json',
            'url',
            '--jq',
            '.[0].url',
          ],
          { optional: true }
        )
      );
      if (url) {
        return { html_url: url };
      }
    }

    const apiHead = existingPullRequestApiHead(repo, headBranch);
    if (!apiHead) {
      return undefined;
    }
    const url = normalizeOptionalUrl(
      runProviderOutput(
        'gh',
        [
          'api',
          '--method',
          'GET',
          `repos/${repo.fullName}/pulls`,
          '-f',
          `head=${apiHead}`,
          '-f',
          'state=open',
          '--jq',
          '.[0].html_url',
        ],
        { optional: true }
      )
    );
    return url ? { html_url: url } : undefined;
  }

  const apiHead = existingPullRequestApiHead(repo, headBranch);
  if (!apiHead) {
    return undefined;
  }
  const query = new URLSearchParams({ head: apiHead, state: 'open' });
  const items = await requestGithub('GET', `${githubPullRequestsApiPath(repo)}?${query.toString()}`);
  return Array.isArray(items) ? items[0] : undefined;
}

async function addGithubPullRequestLabel(repo, prNumber, label) {
  await requestGithub(
    'POST',
    `${githubIssueApiPath({ ...repo, number: prNumber })}/labels`,
    { labels: [label] }
  );
}

async function updateGithubPullRequest(repo, pr, title, body, label) {
  const updated = await requestGithub('PATCH', `${githubPullRequestsApiPath(repo)}/${encodeURIComponent(pr.number)}`, {
    title,
    body,
  });
  await addGithubPullRequestLabel(repo, pr.number, label);
  return updated.html_url || pr.html_url || '';
}

function editGithubPullRequestWithCli(prUrl, title, bodyFile, label, options) {
  runProviderChecked('gh', ['pr', 'edit', prUrl, '--title', title, '--body-file', bodyFile, '--add-label', label], {
    dryRun: options.dryRun,
  });
}

function assertGithubPullRequestBase(existing, baseBranch) {
  const current = existing && existing.base && existing.base.ref;
  if (current && current !== baseBranch) {
    throw new Error(`Existing pull request targets ${current}, expected ${baseBranch}. Refusing to retarget it.`);
  }
}

async function createOrUpdateGithubPullRequest({ repo, title, bodyFile, label, baseBranch, headBranch, draft, options }) {
  if (options.dryRun) {
    const args = [
      'pr',
      'create',
      '--repo',
      repo.fullName,
      '--title',
      title,
      '--body-file',
      bodyFile,
      '--label',
      label,
      '--base',
      baseBranch,
      '--head',
      headBranch,
    ];
    if (draft) {
      args.push('--draft');
    }
    console.log(JSON.stringify({ dryRun: true, command: 'gh', args }, null, 2));
    return `https://github.com/${repo.fullName}/pulls/dry-run`;
  }

  const existing = await findExistingGithubPullRequest(repo, headBranch, options);
  const body = readBodyFile(bodyFile);
  if (existing && existing.html_url) {
    if (!getGithubToken()) {
      const currentBase = runProviderOutput('gh', ['pr', 'view', existing.html_url, '--json', 'baseRefName', '--jq', '.baseRefName']);
      if (currentBase && currentBase !== baseBranch) {
        throw new Error(`Existing pull request targets ${currentBase}, expected ${baseBranch}. Refusing to retarget it.`);
      }
      editGithubPullRequestWithCli(existing.html_url, title, bodyFile, label, options);
      return existing.html_url;
    }
    assertGithubPullRequestBase(existing, baseBranch);
    return updateGithubPullRequest(repo, existing, title, body, label);
  }

  if (!getGithubToken()) {
    const args = [
      'pr',
      'create',
      '--repo',
      repo.fullName,
      '--title',
      title,
      '--body-file',
      bodyFile,
      '--label',
      label,
      '--base',
      baseBranch,
      '--head',
      headBranch,
    ];
    if (draft) {
      args.push('--draft');
    }

    try {
      return runProviderChecked('gh', args);
    } catch (error) {
      if (!isExistingPullRequestError(error)) {
        throw error;
      }
      const duplicate = await findExistingGithubPullRequest(repo, headBranch, options);
      if (!duplicate || !duplicate.html_url) {
        throw error;
      }
      const currentBase = runProviderOutput('gh', ['pr', 'view', duplicate.html_url, '--json', 'baseRefName', '--jq', '.baseRefName']);
      if (currentBase && currentBase !== baseBranch) {
        throw new Error(`Existing pull request targets ${currentBase}, expected ${baseBranch}. Refusing to retarget it.`);
      }
      editGithubPullRequestWithCli(duplicate.html_url, title, bodyFile, label, options);
      return duplicate.html_url;
    }
  }

  try {
    const created = await requestGithub('POST', githubPullRequestsApiPath(repo), {
      title,
      body,
      base: baseBranch,
      head: headBranch,
      draft: Boolean(draft),
    });
    await addGithubPullRequestLabel(repo, created.number, label);
    return created.html_url || '';
  } catch (error) {
    if (!isExistingPullRequestError(error)) {
      throw error;
    }
    const duplicate = await findExistingGithubPullRequest(repo, headBranch, options);
    if (!duplicate || !duplicate.html_url) {
      throw error;
    }
    assertGithubPullRequestBase(duplicate, baseBranch);
    return updateGithubPullRequest(repo, duplicate, title, body, label);
  }
}

async function findExistingGitlabMergeRequest(repo, headBranch, options = {}) {
  if (options.dryRun) {
    return undefined;
  }
  const sourceBranch = encodeURIComponent(String(headBranch || '').trim());
  if (!sourceBranch) {
    return undefined;
  }
  const items = await requestGitlab(
    'GET',
    `${gitlabMergeRequestsApiPath(repo)}?state=opened&source_branch=${sourceBranch}`,
    undefined,
    options
  );
  return Array.isArray(items) ? items[0] : undefined;
}

async function createOrUpdateGitlabMergeRequest({ repo, title, bodyFile, label, baseBranch, headBranch, draft, options }) {
  if (options.dryRun) {
    const url = `${(options.gitlabUrl || process.env.GITLAB_BASE_URL || process.env.CI_SERVER_URL || 'https://gitlab.com').replace(/\/+$/, '')}/${repo.fullName}/-/merge_requests/dry-run`;
    console.log(
      JSON.stringify(
        {
          dryRun: true,
          provider: 'gitlab',
          repo: repo.fullName,
          title,
          baseBranch,
          headBranch,
          label,
          draft: Boolean(draft),
        },
        null,
        2
      )
    );
    return url;
  }

  const existing = await findExistingGitlabMergeRequest(repo, headBranch, options);
  const description = readBodyFile(bodyFile);
  const mrTitle = draft && !/^draft:/i.test(title) ? `Draft: ${title}` : title;
  if (existing && existing.iid) {
    if (existing.target_branch && existing.target_branch !== baseBranch) {
      throw new Error(`Existing merge request targets ${existing.target_branch}, expected ${baseBranch}. Refusing to retarget it.`);
    }
    const updated = await requestGitlab(
      'PUT',
      `${gitlabMergeRequestsApiPath(repo)}/${encodeURIComponent(existing.iid)}`,
      {
        title: mrTitle,
        description,
        add_labels: label,
      },
      options
    );
    return updated.web_url || existing.web_url || '';
  }

  const created = await requestGitlab(
    'POST',
    gitlabMergeRequestsApiPath(repo),
    {
      source_branch: headBranch,
      target_branch: baseBranch,
      title: mrTitle,
      description,
      labels: label,
    },
    options
  );
  return created.web_url || '';
}

async function getGitlabIssueForApply(target, options = {}) {
  return requestGitlab('GET', gitlabIssueApiPath(target), undefined, options);
}

async function applyGitlabLabels(target, labelsToAdd, labelsToRemove, options = {}) {
  const body = {};
  if (labelsToAdd.length > 0) {
    body.add_labels = labelsToAdd.join(',');
  }
  if (labelsToRemove.length > 0) {
    body.remove_labels = labelsToRemove.join(',');
  }
  if (Object.keys(body).length > 0) {
    await requestGitlab('PUT', gitlabIssueApiPath(target), body, options);
  }
}

async function updateGitlabIssueBody(target, body, options = {}) {
  await requestGitlab('PUT', gitlabIssueApiPath(target), { description: body }, options);
}

async function listGitlabIssuesByLabel(repo, label, options = {}) {
  if (options.dryRun) {
    return [];
  }
  const query = new URLSearchParams({
    state: options.state === 'closed' ? 'closed' : 'opened',
    labels: label,
    per_page: String(options.perPage || 30),
  });
  const items = await requestGitlab(
    'GET',
    `/projects/${gitlabProjectRef(repo)}/issues?${query.toString()}`,
    undefined,
    options
  );
  return Array.isArray(items) ? items.map((issue) => normalizeGitlabIssue(issue, repo)) : [];
}

function normalizeGitlabIssue(issue, repo, fallback = {}) {
  return {
    provider: 'gitlab',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    projectId: repo.projectId,
    number: parsePositiveInteger(issue.iid || issue.number || fallback.number, 'issue iid'),
    title: typeof issue.title === 'string' ? issue.title : fallback.title || '',
    body: typeof issue.description === 'string' ? issue.description : fallback.body || '',
    htmlUrl: typeof issue.web_url === 'string' ? issue.web_url : typeof issue.url === 'string' ? issue.url : fallback.htmlUrl || '',
    state: normalizeGitlabState(issue.state || fallback.state),
    author:
      (issue.author && (issue.author.username || issue.author.name)) ||
      fallback.author ||
      '',
    labels: normalizeLabels(issue.labels || fallback.labels),
    milestone: issue.milestone ? normalizeMilestone(issue.milestone) : fallback.milestone || null,
  };
}

function requestGithubWithGh(method, apiPath, body) {
  const args = ['api', apiPath.replace(/^\//, ''), '-X', method];
  let inputPath = '';

  if (body !== undefined) {
    inputPath = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'issue-flow-gh-')), 'body.json');
    fs.writeFileSync(inputPath, JSON.stringify(body), 'utf8');
    args.push('--input', inputPath);
  }

  try {
    const result = spawnSync('gh', args, {
      encoding: 'utf8',
    });
    if (result.error) {
      throw result.error;
    }
    if (result.status !== 0) {
      throw new Error(result.stderr.trim() || `gh api exited with status ${result.status ?? 1}`);
    }
    return parseJsonText(result.stdout.trim());
  } finally {
    if (inputPath) {
      fs.rmSync(path.dirname(inputPath), { recursive: true, force: true });
    }
  }
}

async function requestGithubForApply(method, apiPath, body) {
  if (!getGithubToken()) {
    return requestGithubWithGh(method, apiPath, body);
  }
  return requestGithub(method, apiPath, body);
}

async function getGithubIssueForApply(target) {
  return requestGithubForApply('GET', githubIssueApiPath({ ...target, number: target.issueNumber }));
}

async function applyGithubLabels(target, labelsToAdd, labelsToRemove) {
  const issue = { ...target, number: target.issueNumber };
  for (const label of labelsToRemove) {
    await requestGithubForApply('DELETE', `${githubIssueApiPath(issue)}/labels/${encodeURIComponent(label)}`);
  }

  if (labelsToAdd.length > 0) {
    await requestGithubForApply('POST', `${githubIssueApiPath(issue)}/labels`, { labels: labelsToAdd });
  }
}

async function updateGithubIssueBody(target, body) {
  await requestGithubForApply('PATCH', githubIssueApiPath({ ...target, number: target.issueNumber }), { body });
}

async function listGithubIssuesByLabel(repo, label, options = {}) {
  if (options.dryRun) {
    return [];
  }
  const query = new URLSearchParams({
    state: options.state || 'open',
    labels: label,
    per_page: String(options.perPage || 30),
  });
  const items = await requestGithubForApply(
    'GET',
    `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/issues?${query.toString()}`
  );
  return Array.isArray(items)
    ? items.filter((issue) => !issue.pull_request).map((issue) => normalizeGithubIssue(issue, repo))
    : [];
}

function normalizeGithubIssue(issue, repo, fallback = {}) {
  return {
    provider: 'github',
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    number: parsePositiveInteger(issue.number || fallback.number, 'issue number'),
    title: typeof issue.title === 'string' ? issue.title : fallback.title || '',
    body: typeof issue.body === 'string' ? issue.body : fallback.body || '',
    htmlUrl: typeof issue.html_url === 'string' ? issue.html_url : fallback.htmlUrl || '',
    state: typeof issue.state === 'string' ? issue.state : fallback.state || '',
    author: issue.user && typeof issue.user.login === 'string' ? issue.user.login : fallback.author || '',
    labels: normalizeLabels(issue.labels || fallback.labels),
    milestone: issue.milestone ? normalizeMilestone(issue.milestone) : fallback.milestone || null,
  };
}

async function createGithubIssue({ repo, title, body, labels, milestone }) {
  const payload = {
    title,
    body,
    labels,
    milestone: milestone ? milestone.number : undefined,
  };
  const apiPath = `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/issues`;
  const created = getGithubToken()
    ? await requestGithub('POST', apiPath, payload)
    : await requestGithubWithGh('POST', apiPath, payload);
  return normalizeGithubIssue(created, repo, { title, body, labels });
}

async function listGithubRepositoryIssues(repo) {
  const issues = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGithubForApply('GET', `${githubRepoApiPath(repo)}/issues?state=all&per_page=100&page=${page}`);
    if (!Array.isArray(pageItems) || pageItems.length === 0) break;
    issues.push(...pageItems.filter((issue) => !issue.pull_request).map((issue) => normalizeGithubIssue(issue, repo)));
    if (pageItems.length < 100) break;
  }
  return issues;
}

async function listGithubPlanPullRequests(repo) {
  const pulls = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGithubForApply('GET', `${githubPullRequestsApiPath(repo)}?state=all&per_page=100&page=${page}`);
    if (!Array.isArray(pageItems) || pageItems.length === 0) break;
    pulls.push(...pageItems.filter((pull) => normalizeLabels(pull.labels).includes('mr-by::plan')).map((pull) => normalizeGithubPullRequest(pull, repo)));
    if (pageItems.length < 100) break;
  }
  return pulls;
}

async function readGithubRepositoryFile(repo, ref, filePath) {
  const encodedPath = String(filePath).split('/').map(encodeURIComponent).join('/');
  const result = await requestGithubForApply('GET', `${githubRepoApiPath(repo)}/contents/${encodedPath}?ref=${encodeURIComponent(ref)}`);
  if (!result || result.type !== 'file' || !result.content) throw new Error(`Repository file not found: ${filePath}`);
  return Buffer.from(String(result.content).replace(/\s+/g, ''), 'base64').toString('utf8');
}

async function closeGithubPullRequest(pr) {
  return requestGithubForApply('PATCH', githubPullRequestApiPath(pr), { state: 'closed' });
}

async function closeGithubIssue(issue) {
  return requestGithubForApply('PATCH', githubIssueApiPath(issue), { state: 'closed' });
}

function githubLabelApiPath(repo, labelName) {
  return `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/labels/${encodeURIComponent(labelName)}`;
}

async function getGithubLabel(repo, labelName) {
  try {
    return await requestGithubForApply('GET', githubLabelApiPath(repo, labelName));
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (error.status === 404 || /\b404\b|not found/i.test(message)) {
      return undefined;
    }
    throw error;
  }
}

async function createGithubLabel(repo, definition) {
  const payload = providerLabelDefinition('github', definition);
  return requestGithubForApply('POST', `/repos/${encodeURIComponent(repo.owner)}/${encodeURIComponent(repo.repo)}/labels`, payload);
}

async function updateGithubLabel(repo, definition) {
  const payload = providerLabelDefinition('github', definition);
  return requestGithubForApply('PATCH', githubLabelApiPath(repo, definition.name), {
    new_name: payload.name,
    color: payload.color,
    description: payload.description,
  });
}

async function ensureGithubLabelDefinition(repo, definition, options = {}) {
  if (options.dryRun) {
    return {
      name: definition.name,
      action: 'ensure',
      definition: providerLabelDefinition('github', definition),
    };
  }

  const existing = await getGithubLabel(repo, definition.name);
  const action = planLabelSync('github', existing, definition);
  if (action === 'create') {
    await createGithubLabel(repo, definition);
    return { name: definition.name, action: 'created' };
  }
  if (action === 'update') {
    await updateGithubLabel(repo, definition);
    return { name: definition.name, action: 'updated' };
  }
  return { name: definition.name, action: 'skipped' };
}

function gitlabLabelApiPath(repo, labelName) {
  return `/projects/${gitlabProjectRef(repo)}/labels/${encodeURIComponent(labelName)}`;
}

async function getGitlabLabel(repo, labelName, options = {}) {
  try {
    return await requestGitlab('GET', gitlabLabelApiPath(repo, labelName), undefined, options);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (error.status === 404 || /\b404\b|not found/i.test(message)) {
      return undefined;
    }
    throw error;
  }
}

async function createGitlabLabel(repo, definition, options = {}) {
  return requestGitlab('POST', `/projects/${gitlabProjectRef(repo)}/labels`, providerLabelDefinition('gitlab', definition), options);
}

async function updateGitlabLabel(repo, definition, options = {}) {
  const payload = providerLabelDefinition('gitlab', definition);
  return requestGitlab(
    'PUT',
    gitlabLabelApiPath(repo, definition.name),
    {
      new_name: payload.name,
      color: payload.color,
      description: payload.description,
    },
    options
  );
}

async function ensureGitlabLabelDefinition(repo, definition, options = {}) {
  if (options.dryRun) {
    return {
      name: definition.name,
      action: 'ensure',
      definition: providerLabelDefinition('gitlab', definition),
    };
  }

  const existing = await getGitlabLabel(repo, definition.name, options);
  const action = planLabelSync('gitlab', existing, definition);
  if (action === 'create') {
    await createGitlabLabel(repo, definition, options);
    return { name: definition.name, action: 'created' };
  }
  if (action === 'update') {
    await updateGitlabLabel(repo, definition, options);
    return { name: definition.name, action: 'updated' };
  }
  return { name: definition.name, action: 'skipped' };
}

async function preflightGitlabManagedLabels(repo, definitions = [], options = {}) {
  for (const definition of definitions) {
    const existing = await getGitlabLabel(repo, definition.name, options);
    if (!labelMatchesDefinition('gitlab', existing, definition)) {
      throw new Error(
        `GitLab managed label ${definition.name} is missing or drifted. Run sync-labels.cjs before create-issue.cjs.`
      );
    }
  }
}

async function createGitlabIssue({ repo, title, body, labels, milestone, managedLabelDefinitions = [], options = {} }) {
  await preflightGitlabManagedLabels(repo, managedLabelDefinitions, options);
  const payload = {
    title,
    description: body,
  };
  if (labels.length > 0) {
    payload.labels = labels.join(',');
  }
  if (milestone) payload.milestone_id = milestone.id;
  const created = await requestGitlab('POST', `/projects/${gitlabProjectRef(repo)}/issues`, payload, options);
  return normalizeGitlabIssue(created, repo, { title, body, labels });
}

async function listGitlabRepositoryIssues(repo, options = {}) {
  const issues = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGitlab('GET', `/projects/${gitlabProjectRef(repo)}/issues?state=all&scope=all&per_page=100&page=${page}`, undefined, options);
    if (!Array.isArray(pageItems) || pageItems.length === 0) break;
    issues.push(...pageItems.map((issue) => normalizeGitlabIssue(issue, repo)));
    if (pageItems.length < 100) break;
  }
  return issues;
}

async function listGitlabPlanPullRequests(repo, options = {}) {
  const pulls = [];
  for (let page = 1; page <= 10; page += 1) {
    const pageItems = await requestGitlab('GET', `/projects/${gitlabProjectRef(repo)}/merge_requests?scope=all&state=all&labels=${encodeURIComponent('mr-by::plan')}&per_page=100&page=${page}`, undefined, options);
    if (!Array.isArray(pageItems) || pageItems.length === 0) break;
    pulls.push(...pageItems.map((pull) => normalizeGitlabPullRequest(pull, repo)));
    if (pageItems.length < 100) break;
  }
  return pulls;
}

async function readGitlabRepositoryFile(repo, ref, filePath, options = {}) {
  const result = await requestGitlab('GET', `/projects/${gitlabProjectRef(repo)}/repository/files/${encodeURIComponent(filePath)}?ref=${encodeURIComponent(ref)}`, undefined, options);
  if (!result || !result.content) throw new Error(`Repository file not found: ${filePath}`);
  return Buffer.from(String(result.content).replace(/\s+/g, ''), result.encoding || 'base64').toString('utf8');
}

async function closeGitlabPullRequest(pr, options = {}) {
  return requestGitlab('PUT', gitlabMergeRequestApiPath(pr), { state_event: 'close' }, options);
}

async function closeGitlabIssue(issue, options = {}) {
  return requestGitlab('PUT', gitlabIssueApiPath(issue), { state_event: 'close' }, options);
}

function parsePositiveInteger(value, name) {
  const parsed = Number.parseInt(String(value), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function pickNumber(options, keys, label) {
  for (const key of keys) {
    if (options[key] !== undefined && options[key] !== '') {
      return parsePositiveInteger(options[key], label);
    }
  }
  throw new Error(`${label} is required`);
}

function buildPortRef(provider, payload, options, kind) {
  const repo = provider.resolveRepo(payload, options);
  const number = kind === 'issue'
    ? pickNumber(options, ['issueNumber', 'issue'], '--issue')
    : pickNumber(options, ['prNumber', 'pr'], '--pr');
  return {
    provider: provider.name,
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    projectId: repo.projectId,
    issueNumber: number,
    number,
  };
}

function normalizePortSubject(subject) {
  return {
    provider: subject.provider,
    repo: subject.repoFullName,
    number: subject.number,
    title: subject.title || '',
    body: subject.body || '',
    url: subject.htmlUrl || subject.url || '',
    state: subject.state || '',
    labels: Array.isArray(subject.labels) ? subject.labels : [],
    author: subject.author || '',
    draft: Boolean(subject.draft),
    merged: Boolean(subject.merged),
    baseRef: subject.baseRef || '',
    headRef: subject.headRef || '',
    headSha: subject.headSha || '',
    milestone: subject.milestone || null,
  };
}

function normalizePortComment(comment) {
  const author = comment && typeof comment.author === 'string'
    ? comment.author
    : comment && comment.user && comment.user.login
      ? comment.user.login
      : comment && comment.author && (comment.author.username || comment.author.name)
        ? comment.author.username || comment.author.name
        : '';
  return {
    commentId: String(comment && comment.id !== undefined ? comment.id : ''),
    body: String((comment && (comment.body || comment.note)) || ''),
    url: String((comment && (comment.html_url || comment.htmlUrl || comment.web_url || comment.url)) || ''),
    author,
    createdAt: String((comment && (comment.created_at || comment.createdAt)) || ''),
    updatedAt: String((comment && (comment.updated_at || comment.updatedAt)) || ''),
  };
}

function normalizePortReviewComment(comment) {
  const position = (comment && comment.position) || {};
  const author = comment && typeof comment.author === 'string'
    ? comment.author
    : comment && comment.user && comment.user.login
      ? comment.user.login
      : comment && comment.author && (comment.author.username || comment.author.name)
        ? comment.author.username || comment.author.name
        : '';
  const line = comment && (comment.line ?? comment.new_line ?? position.new_line ?? position.line);
  const startLine = comment && (comment.start_line ?? position.start_line);
  return {
    commentId: String(comment && comment.id !== undefined ? comment.id : ''),
    reviewId: String((comment && (comment.pull_request_review_id || comment.reviewId || comment.pullRequestReviewId)) || ''),
    discussionId: String((comment && (comment.discussionId || comment.discussion_id)) || ''),
    body: String((comment && (comment.body || comment.note)) || ''),
    url: String((comment && (comment.html_url || comment.htmlUrl || comment.web_url || comment.url)) || ''),
    author,
    createdAt: String((comment && (comment.created_at || comment.createdAt)) || ''),
    updatedAt: String((comment && (comment.updated_at || comment.updatedAt)) || ''),
    path: String((comment && (comment.path || comment.new_path || position.new_path || position.old_path)) || ''),
    line: line == null ? null : Number(line),
    startLine: startLine == null ? null : Number(startLine),
    side: String((comment && (comment.side || position.side)) || ''),
    startSide: String((comment && (comment.start_side || position.start_side)) || ''),
    diffHunk: String((comment && comment.diff_hunk) || ''),
    commitId: String((comment && (comment.commit_id || position.head_sha)) || ''),
    originalCommitId: String((comment && comment.original_commit_id) || ''),
    position: comment && comment.position !== undefined ? comment.position : null,
    originalPosition: comment && comment.original_position !== undefined ? comment.original_position : null,
    resolved: comment && comment.resolved !== undefined ? Boolean(comment.resolved) : null,
    resolvable: comment && comment.resolvable !== undefined ? Boolean(comment.resolvable) : null,
  };
}

function normalizePortCommentResult(comment, fallback = {}) {
  const normalized = normalizePortComment(comment || fallback);
  return {
    commentId: normalized.commentId || String(fallback.commentId || ''),
    url: normalized.url || String(fallback.url || ''),
    data: comment || fallback,
  };
}

function assertReactionContent(content) {
  const normalized = String(content || 'eyes').trim();
  const allowed = new Set(['eyes']);
  if (!allowed.has(normalized)) {
    throw new Error(`--content must be one of: ${[...allowed].join(', ')}`);
  }
  return normalized;
}

function resolveProviderPort(options = {}, payload = {}) {
  const provider = resolveProvider(options, payload);
  const baseOptions = { ...options, provider: provider.name };

  const issueRef = () => buildPortRef(provider, payload, baseOptions, 'issue');
  const pullRequestRef = () => buildPortRef(provider, payload, baseOptions, 'pr');

  return {
    provider: provider.name,
    issues: {
      async get() {
        const issue = issueRef();
        if (baseOptions.dryRun) {
          return normalizePortSubject(issue);
        }
        const current = provider.getIssueForApply
          ? await provider.getIssueForApply(issue, baseOptions)
          : await provider.fetchCurrentIssue(issue, baseOptions);
        return normalizePortSubject(provider.name === 'gitlab'
          ? normalizeGitlabIssue(current, issue, issue)
          : normalizeGithubIssue(current, issue, issue));
      },
      async listComments() {
        const issue = issueRef();
        const comments = await provider.listIssueComments(issue, baseOptions);
        return comments.map(normalizePortComment);
      },
      async createComment(input = {}) {
        const issue = issueRef();
        const comment = await provider.createIssueComment(issue, input.body || '', baseOptions);
        return normalizePortCommentResult(comment, { commentId: 'dry-run-comment' });
      },
      async updateComment(commentRef = {}, input = {}) {
        const issue = issueRef();
        await provider.updateIssueComment(issue, commentRef.commentId, input.body || '', baseOptions);
        return { commentId: String(commentRef.commentId), updated: true };
      },
      async deleteComment(commentRef = {}) {
        const issue = issueRef();
        await provider.deleteIssueComment(issue, commentRef.commentId, baseOptions);
        return { commentId: String(commentRef.commentId), deleted: true };
      },
      async acknowledge(input = {}) {
        const issue = issueRef();
        const content = assertReactionContent(input.content);
        await provider.addIssueReaction(issue, content, baseOptions);
        return { content, acknowledged: true };
      },
      async createReaction(input = {}) {
        const issue = issueRef();
        const content = assertReactionContent(input.content);
        await provider.addIssueReaction(issue, content, baseOptions);
        return { content, created: true };
      },
    },
    pullRequests: {
      async get() {
        const pr = pullRequestRef();
        if (baseOptions.dryRun) {
          return normalizePortSubject(pr);
        }
        const current = await provider.fetchCurrentPullRequest(pr, baseOptions);
        return normalizePortSubject(current);
      },
      async listComments() {
        const pr = pullRequestRef();
        const comments = await provider.listPullRequestComments(pr, baseOptions);
        return comments.map(normalizePortComment);
      },
      async listReviewComments() {
        const pr = pullRequestRef();
        const comments = await provider.listPullRequestReviewComments(pr, baseOptions);
        return comments.map(normalizePortReviewComment);
      },
      async createComment(input = {}) {
        const pr = pullRequestRef();
        const comment = await provider.createPullRequestComment(pr, input.body || '', baseOptions);
        return normalizePortCommentResult(comment, { commentId: 'dry-run-comment' });
      },
      async updateComment(commentRef = {}, input = {}) {
        const pr = pullRequestRef();
        await provider.updatePullRequestComment(pr, commentRef.commentId, input.body || '', baseOptions);
        return { commentId: String(commentRef.commentId), updated: true };
      },
      async deleteComment(commentRef = {}) {
        const pr = pullRequestRef();
        await provider.deletePullRequestComment(pr, commentRef.commentId, baseOptions);
        return { commentId: String(commentRef.commentId), deleted: true };
      },
    },
  };
}

const githubProvider = {
  name: 'github',
  envEventPath: 'GITHUB_EVENT_PATH',
  envEventName: 'GITHUB_EVENT_NAME',
  hasToken: () => Boolean(getGithubToken()),
  resolveRepo: resolveGithubRepo,
  buildIssueContext: buildGithubIssueContext,
  buildPullRequestContext: buildGithubPullRequestContext,
  isPullRequestIssue: (payload) => Boolean(payload.issue && payload.issue.pull_request),
  isBotComment: (payload) => Boolean(payload.comment && payload.comment.user && payload.comment.user.type === 'Bot'),
  getCommentContext: getGithubCommentContext,
  isReviewCommentCreatedEvent: isGithubReviewCommentCreatedEvent,
  getReviewCommentContext: getGithubReviewCommentContext,
  fetchCurrentIssue: fetchCurrentGithubIssue,
  fetchCurrentPullRequest: fetchCurrentGithubPullRequest,
  listIssueComments: listGithubIssueComments,
  createIssueComment: createGithubIssueComment,
  updateIssueComment: updateGithubIssueComment,
  deleteIssueComment: deleteGithubIssueComment,
  listPullRequestComments: listGithubIssueComments,
  listPullRequestReviewComments: listGithubPullRequestReviewComments,
  createPullRequestComment: createGithubIssueComment,
  submitPullRequestReview: submitGithubPullRequestReview,
  updatePullRequestComment: updateGithubIssueComment,
  deletePullRequestComment: deleteGithubIssueComment,
  addTriggerCommentReaction: addGithubTriggerCommentReaction,
  addReviewCommentReaction: addGithubReviewCommentReaction,
  addIssueReaction: addGithubIssueReaction,
  issueApiPath: githubIssueApiPath,
  pullRequestApiPath: githubPullRequestApiPath,
  issueCommentApiPath: githubIssueCommentApiPath,
  issueReactionApiPath: githubIssueReactionApiPath,
  getIssueForApply: getGithubIssueForApply,
  applyLabels: applyGithubLabels,
  updateIssueBody: updateGithubIssueBody,
  createIssue: createGithubIssue,
  listRepositoryIssues: listGithubRepositoryIssues,
  listPlanPullRequests: listGithubPlanPullRequests,
  readRepositoryFile: readGithubRepositoryFile,
  closePullRequest: closeGithubPullRequest,
  closeIssue: closeGithubIssue,
  listIssuesByLabel: listGithubIssuesByLabel,
  collectWorkflowRunFailureDetails: collectGithubWorkflowRunFailureDetails,
  getLabel: getGithubLabel,
  ensureLabelDefinition: ensureGithubLabelDefinition,
  ensurePullRequestLabel: ensureGithubPullRequestLabel,
  findExistingPullRequest: findExistingGithubPullRequest,
  createOrUpdatePullRequest: createOrUpdateGithubPullRequest,
  getDefaultBranch: getGithubDefaultBranch,
  branchExists: githubBranchExists,
  listMilestones: listGithubMilestones,
  getMilestoneByTitle: getGithubMilestoneByTitle,
  ensureMilestone: ensureGithubMilestone,
  closeMilestone: closeGithubMilestone,
  setIssueMilestone: setGithubIssueMilestone,
};

const gitlabProvider = {
  name: 'gitlab',
  envEventPath: 'GITLAB_EVENT_PATH',
  envEventName: 'GITLAB_EVENT_NAME',
  hasToken: (options) => Boolean(getGitlabToken(options)) || hasGlab(),
  resolveRepo: resolveGitlabRepo,
  buildIssueContext: buildGitlabIssueContext,
  buildPullRequestContext: buildGitlabPullRequestContext,
  isPullRequestIssue: isGitlabMergeRequestOrNonIssue,
  isBotComment: isGitlabBotComment,
  getCommentContext: getGitlabCommentContext,
  isReviewCommentCreatedEvent: isGitlabReviewCommentCreatedEvent,
  getReviewCommentContext: getGitlabReviewCommentContext,
  fetchCurrentIssue: fetchCurrentGitlabIssue,
  fetchCurrentPullRequest: fetchCurrentGitlabPullRequest,
  listIssueComments: listGitlabIssueComments,
  createIssueComment: createGitlabIssueComment,
  updateIssueComment: updateGitlabIssueComment,
  deleteIssueComment: deleteGitlabIssueComment,
  listPullRequestComments: listGitlabPullRequestComments,
  listPullRequestReviewComments: listGitlabPullRequestReviewComments,
  createPullRequestComment: createGitlabPullRequestComment,
  submitPullRequestReview: submitGitlabPullRequestReview,
  updatePullRequestComment: updateGitlabPullRequestComment,
  deletePullRequestComment: deleteGitlabPullRequestComment,
  addTriggerCommentReaction: addGitlabTriggerCommentReaction,
  addReviewCommentReaction: addGitlabReviewCommentReaction,
  addIssueReaction: addGitlabIssueReaction,
  issueApiPath: gitlabIssueApiPath,
  pullRequestApiPath: gitlabMergeRequestApiPath,
  getIssueForApply: getGitlabIssueForApply,
  applyLabels: applyGitlabLabels,
  updateIssueBody: updateGitlabIssueBody,
  createIssue: createGitlabIssue,
  listRepositoryIssues: listGitlabRepositoryIssues,
  listPlanPullRequests: listGitlabPlanPullRequests,
  readRepositoryFile: readGitlabRepositoryFile,
  closePullRequest: closeGitlabPullRequest,
  closeIssue: closeGitlabIssue,
  listIssuesByLabel: listGitlabIssuesByLabel,
  createOrUpdateMergeRequest: createOrUpdateGitlabMergeRequest,
  getLabel: getGitlabLabel,
  ensureLabelDefinition: ensureGitlabLabelDefinition,
  createOrUpdatePullRequest: createOrUpdateGitlabMergeRequest,
  getDefaultBranch: getGitlabDefaultBranch,
  branchExists: gitlabBranchExists,
  listMilestones: listGitlabMilestones,
  getMilestoneByTitle: getGitlabMilestoneByTitle,
  ensureMilestone: ensureGitlabMilestone,
  closeMilestone: closeGitlabMilestone,
  setIssueMilestone: setGitlabIssueMilestone,
};

const providers = {
  github: githubProvider,
  gitlab: gitlabProvider,
};

module.exports = {
  detectProvider,
  existingPullRequestApiHead,
  extractTokenFromRemoteUrl,
  gitlabApiBodyArgs,
  getGitlabToken,
  gitlabApiBaseUrl,
  gitlabHostname,
  headBranchFilterCandidates,
  isExistingPullRequestError,
  labelMatchesDefinition,
  normalizeGitlabState,
  normalizeLabelName,
  normalizeLabels,
  normalizeOptionalUrl,
  normalizeProviderName,
  parseGitRemoteUrl,
  parseRepoFullName,
  planLabelSync,
  providerLabelDefinition,
  providers,
  requestGitlab,
  resolveProvider,
  resolveProviderPort,
};
