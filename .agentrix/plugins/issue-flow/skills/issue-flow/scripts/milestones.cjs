const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_CONFIG_PATH = '.issue-flow/config.json';
const DEFAULT_BRANCH_PATTERNS = ['release/*', 'integration/*'];

function readProjectConfig(options = {}) {
  const configPath = path.resolve(process.cwd(), options.config || process.env.ISSUE_FLOW_CONFIG || DEFAULT_CONFIG_PATH);
  if (!fs.existsSync(configPath)) return {};
  return JSON.parse(fs.readFileSync(configPath, 'utf8'));
}

function resolveMilestoneConfig(options = {}) {
  const raw = readProjectConfig(options).milestone;
  if (!raw || raw.enabled !== true) {
    return { enabled: false, branchPatterns: DEFAULT_BRANCH_PATTERNS };
  }
  const branchPatterns = raw.branchPatterns === undefined ? DEFAULT_BRANCH_PATTERNS : raw.branchPatterns;
  if (!Array.isArray(branchPatterns) || branchPatterns.some((pattern) => typeof pattern !== 'string' || !pattern.trim())) {
    throw new Error('milestone.branchPatterns must be an array of non-empty strings');
  }
  return { enabled: true, branchPatterns: [...new Set(branchPatterns.map((pattern) => pattern.trim()))] };
}

function escapeRegExp(value) {
  return value.replace(/[|\\{}()[\]^$+?.]/g, '\\$&');
}

function matchesBranchPattern(branch, pattern) {
  const source = String(pattern || '').split('*').map(escapeRegExp).join('.*');
  return new RegExp(`^${source}$`).test(String(branch || ''));
}

function matchesConfiguredBranch(branch, config) {
  return config.branchPatterns.some((pattern) => matchesBranchPattern(branch, pattern));
}

function milestoneTitle(issue = {}) {
  const milestone = issue.milestone;
  if (typeof milestone === 'string') return milestone.trim();
  return String((milestone && milestone.title) || issue.milestoneTitle || '').trim();
}

async function resolveIssueTarget(issue, provider, repo, options = {}) {
  const config = resolveMilestoneConfig(options);
  if (!config.enabled) return { enabled: false, baseRef: '', checkoutRef: '', milestone: '' };

  const title = milestoneTitle(issue);
  const baseRef = title || await provider.getDefaultBranch(repo, options);
  if (!baseRef) throw new Error('Unable to resolve the repository default branch for milestone none');
  if (title && config.branchPatterns.length > 0 && !matchesConfiguredBranch(title, config)) {
    throw new Error(`Milestone ${title} does not match milestone.branchPatterns`);
  }
  if (options.dryRun) return { enabled: true, baseRef, checkoutRef: baseRef, milestone: title };
  if (title) {
    const milestone = await provider.getMilestoneByTitle(repo, title, options);
    if (!milestone || milestone.state !== 'open') throw new Error(`Milestone ${title} is not open`);
  }
  if (!await provider.branchExists(repo, baseRef, options)) {
    throw new Error(`Target branch does not exist: ${baseRef}`);
  }
  return { enabled: true, baseRef, checkoutRef: baseRef, milestone: title };
}

async function listMilestoneCandidates(provider, repo, options = {}) {
  const config = resolveMilestoneConfig(options);
  if (!config.enabled) return [];
  const milestones = await provider.listMilestones(repo, 'open', options);
  const candidates = [];
  for (const milestone of milestones) {
    const title = String(milestone.title || '').trim();
    if (!title || (config.branchPatterns.length > 0 && !matchesConfiguredBranch(title, config))) continue;
    if (await provider.branchExists(repo, title, options)) candidates.push(milestone);
  }
  return candidates;
}

async function resolveMilestoneSelection(value, provider, repo, options = {}) {
  const config = resolveMilestoneConfig(options);
  if (!config.enabled) return { enabled: false, milestone: undefined };
  if (value === undefined) {
    throw new Error('Milestone selection is required while the milestone feature is enabled. Pass --milestone <title|none>.');
  }
  const title = String(value).trim();
  if (title === 'none') return { enabled: true, milestone: null };
  if (!title) throw new Error('--milestone must be a milestone title or none');
  if (config.branchPatterns.length > 0 && !matchesConfiguredBranch(title, config)) {
    throw new Error(`Milestone ${title} does not match milestone.branchPatterns`);
  }
  if (options.dryRun) return { enabled: true, milestone: { title, state: 'open' } };
  const milestone = await provider.getMilestoneByTitle(repo, title, options);
  if (!milestone || milestone.state !== 'open') throw new Error(`Milestone ${title} is not open`);
  if (!await provider.branchExists(repo, title, options)) throw new Error(`Target branch does not exist: ${title}`);
  return { enabled: true, milestone };
}

module.exports = {
  DEFAULT_BRANCH_PATTERNS,
  listMilestoneCandidates,
  matchesBranchPattern,
  matchesConfiguredBranch,
  milestoneTitle,
  readProjectConfig,
  resolveIssueTarget,
  resolveMilestoneConfig,
  resolveMilestoneSelection,
};
