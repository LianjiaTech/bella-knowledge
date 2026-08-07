const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  allOptimizationProposalsTerminal,
  childOptimizationState,
  deriveOptimizationProposalStates,
  optimizationSourceIssueNumber,
  parseOptimizationProposalMarker,
  parsePlanArtifactMarker,
} = require('../../../domain/index.cjs');

function parseProposalMarker(body = '') {
  return parseOptimizationProposalMarker(body);
}

function parseArtifactMarker(pullRequest = {}) {
  const marker = parsePlanArtifactMarker(pullRequest.body);
  return marker && marker.artifact === 'optimization' ? {
    issueNumber: marker.issueNumber,
    branch: marker.branch,
    commit: marker.commit,
    path: marker.path,
  } : undefined;
}

function sourceIssueNumber(body = '') {
  return optimizationSourceIssueNumber(body);
}

function issueBody(issue = {}) {
  return issue.body || issue.description || '';
}

function terminalState(issue = {}) {
  const state = childOptimizationState(issue);
  return state === 'completed' || state === 'cancelled' ? state : '';
}

function runApply(provider, repo, issueNumber, args, options = {}) {
  const command = [
    path.join(__dirname, 'apply.cjs'),
    '--issue-number', String(issueNumber),
    '--provider', provider.name,
    '--repo', repo.fullName,
    ...args,
  ];
  if (options.dryRun) command.push('--dry-run');
  const result = spawnSync('node', command, { encoding: 'utf8', stdio: options.dryRun ? 'pipe' : 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error((result.stderr || result.stdout || '').trim() || `apply.cjs exited with status ${result.status ?? 1}`);
}

function proposalStates(data, optimizationIssueNumber, comments, issues) {
  return deriveOptimizationProposalStates(data, optimizationIssueNumber, comments, issues)
    .map(({ childIssue, ...state }) => ({ ...state, state: state.state === 'created' || state.state === 'executing' ? 'active' : state.state }));
}

async function completeOptimizationForChildIssue(provider, repo, childIssueNumber, options = {}) {
  if (!provider.listRepositoryIssues || !provider.listPlanPullRequests || !provider.readRepositoryFile || !provider.closePullRequest || !provider.closeIssue) return { completed: false, reason: 'provider_not_supported' };
  const child = await provider.getIssueForApply({ ...repo, provider: provider.name, issueNumber: childIssueNumber, number: childIssueNumber }, options);
  const relation = parseProposalMarker(issueBody(child));
  if (!relation) return { completed: false, reason: 'not_optimization_proposal' };
  if (!terminalState(child)) return { completed: false, reason: 'proposal_not_terminal' };
  const issues = await provider.listRepositoryIssues(repo, options);
  const optimizationIssue = issues.find((issue) => issue.number === relation.optimizationIssueNumber);
  if (!optimizationIssue) throw new Error(`Optimization issue #${relation.optimizationIssueNumber} was not found`);
  const sourceNumber = sourceIssueNumber(optimizationIssue.body) || relation.sourceIssueNumber;
  const candidates = (await provider.listPlanPullRequests(repo, options)).map((pullRequest) => ({ pullRequest, marker: parseArtifactMarker(pullRequest) }))
    .filter((item) => item.marker && item.marker.issueNumber === relation.optimizationIssueNumber)
    .sort((left, right) => Number(['open', 'opened'].includes(right.pullRequest.state)) - Number(['open', 'opened'].includes(left.pullRequest.state)));
  const selected = candidates[0];
  if (!selected) throw new Error(`Optimization Plan MR for issue #${relation.optimizationIssueNumber} was not found`);
  const data = JSON.parse(await provider.readRepositoryFile(repo, selected.marker.commit, selected.marker.path, options));
  if (!data || data.artifact !== 'optimization' || !Array.isArray(data.proposals)) throw new Error('Optimization Plan artifact is invalid');
  const comments = await provider.listPullRequestComments(selected.pullRequest, options);
  const states = proposalStates(data, relation.optimizationIssueNumber, comments, issues.map((issue) => issue.number === child.number ? child : issue));
  if (!allOptimizationProposalsTerminal(states)) return { completed: false, reason: 'proposals_pending', proposals: states };
  await provider.closePullRequest(selected.pullRequest, options);
  runApply(provider, repo, relation.optimizationIssueNumber, ['--status', 'status::done', '--clear-flow'], options);
  if (!options.dryRun) await provider.closeIssue({ ...optimizationIssue, ...repo, issueNumber: optimizationIssue.number }, options);
  runApply(provider, repo, sourceNumber, ['--optimization-state', 'optimization::analyzed'], options);
  return { completed: true, optimizationIssueNumber: relation.optimizationIssueNumber, sourceIssueNumber: sourceNumber, proposals: states };
}

module.exports = {
  allOptimizationProposalsTerminal,
  completeOptimizationForChildIssue,
  parseArtifactMarker,
  parseProposalMarker,
  proposalStates,
  sourceIssueNumber,
};
