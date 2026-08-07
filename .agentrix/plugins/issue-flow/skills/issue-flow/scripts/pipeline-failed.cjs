#!/usr/bin/env node

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { gitlabBridgeValue, loadEventPayload } = require('./events.cjs');
const { resolveProvider } = require('./providers.cjs');
const { computeLabelChanges } = require('./apply.cjs');

const MARKER_NAME = 'issue-flow:pipeline-failure';
const FAILURE_LABEL = 'failure::ci';
const HASH_LABEL_PREFIX = 'ci-fp::';
const FAILURE_INTAKE_WORKFLOW_NAME = 'Issue Flow Failure Intake';
const ISSUE_PRIORITY = 'priority::p2';
const LOG_LIMIT = 12000;

const VALUE_OPTIONS = new Set([
  '--event',
  '--provider',
  '--repo',
  '--log-file',
  '--gitlab-url',
  '--gitlab-api-url',
  '--gitlab-project',
  '--gitlab-token',
]);

function usage() {
  return [
    'Usage: pipeline-failed.cjs [options]',
    '',
    'Analyze a failed CI pipeline/job and create or update a deduped issue-flow issue for agent review.',
    '',
    'Options:',
    '  --event <path>          Provider event JSON path.',
    '  --provider <provider>   github or gitlab. Defaults from event/environment.',
    '  --repo <owner/repo>     Repository/project override.',
    '  --log-file <path>       Optional failure log file for GitLab bridge or manual runs.',
    '  --dry-run               Analyze and print intended behavior without provider writes.',
    '  --help',
  ].join('\n');
}

function parseArgs(argv) {
  const options = { _: [] };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--help') {
      options.help = true;
      continue;
    }
    if (arg === '--dry-run') {
      options.dryRun = true;
      continue;
    }
    if (!arg.startsWith('--')) {
      options._.push(arg);
      continue;
    }
    if (!VALUE_OPTIONS.has(arg)) {
      throw new Error(`Unknown option: ${arg}`);
    }
    const key = arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for ${arg}`);
    }
    options[key] = value;
    index += 1;
  }
  return options;
}

function sha256(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

function truncate(value, limit) {
  const text = String(value || '');
  return text.length > limit ? `${text.slice(0, limit)}\n[truncated]` : text;
}

function normalizeSignatureLine(value) {
  return String(value || '')
    .replace(/\b[0-9a-f]{40}\b/gi, '<sha>')
    .replace(/\b[0-9a-f]{7,12}\b/gi, '<sha>')
    .replace(/https?:\/\/\S+/gi, '<url>')
    .replace(/\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\b/g, '<timestamp>')
    .replace(/\b\d{2}:\d{2}:\d{2}\b/g, '<time>')
    .replace(/\s+/g, ' ')
    .trim();
}

function compactLogSummary(log) {
  const lines = String(log || '')
    .split(/\r?\n/)
    .map(normalizeSignatureLine)
    .filter(Boolean);
  const interesting = lines.filter((line) =>
    /(error|fail|failed|fatal|exception|denied|not found|missing|permission|secret|runner|assert|eslint|tsc|syntax|typeerror|referenceerror)/i.test(line)
  );
  return (interesting.length > 0 ? interesting : lines).slice(0, 12).join('\n');
}

function firstSummaryLine(summary, context = {}) {
  const line = String(summary || '').split(/\r?\n/).find(Boolean);
  if (line) {
    return line;
  }
  return [context.workflowName, context.jobName, context.stepName].filter(Boolean).join(' / ') || 'log unavailable';
}

function analyzeFailureContext(context) {
  const logSummary = compactLogSummary(context.log);
  return {
    category: 'agent_review_required',
    failureKind: 'ci_failure',
    suspectedFixScope: 'agent_to_determine',
    confidence: 'unreviewed',
    normalizedErrorSignature: firstSummaryLine(logSummary, context),
    rootCause: 'CI failed. The issue-flow agent must inspect the linked run, logs, repository, and workflow configuration to determine whether the root cause is a repository change, provider configuration, transient infrastructure, or a false positive.',
    humanActions: [
      'Let the issue-flow agent determine the root cause and next state from the CI context.',
    ],
    validation: 'Agent should choose and run the smallest relevant validation, then re-run or reference the failed CI workflow when appropriate.',
    logSummary,
  };
}

function buildFingerprintInput(context, analysis) {
  return [
    `provider=${context.provider}`,
    `repo=${context.repoFullName}`,
    `failure_kind=${analysis.failureKind}`,
    `workflow_or_pipeline_name=${context.workflowName}`,
    `job_name=${context.jobName}`,
    `step_name=${context.stepName}`,
    `normalized_error_signature=${analysis.normalizedErrorSignature}`,
    `suspected_fix_scope=${analysis.suspectedFixScope}`,
  ].join('\n');
}

function fingerprintFailure(context, analysis) {
  const input = buildFingerprintInput(context, analysis);
  const hash = sha256(input);
  return {
    input,
    full: `sha256:${hash}`,
    hash8: hash.slice(0, 8),
    label: `${HASH_LABEL_PREFIX}${hash.slice(0, 8)}`,
  };
}

function buildMarker(context, fingerprint) {
  return [
    '<!-- issue-flow:pipeline-failure',
    `fingerprint: ${fingerprint.full}`,
    `provider: ${context.provider}`,
    `workflow: ${context.workflowName || ''}`,
    `job: ${context.jobName || ''}`,
    `step: ${context.stepName || ''}`,
    '-->',
  ].join('\n');
}

function parsePipelineFailureMarker(body) {
  const match = String(body || '').match(/<!--\s*issue-flow:pipeline-failure([\s\S]*?)-->/i);
  if (!match) {
    return undefined;
  }
  const fields = {};
  for (const line of match[1].split(/\r?\n/)) {
    const pair = line.match(/^\s*([a-z_]+):\s*(.*?)\s*$/i);
    if (pair) {
      fields[pair[1].toLowerCase()] = pair[2];
    }
  }
  return fields;
}

function findMatchingIssue(issues, fingerprint) {
  return (issues || []).find((issue) => {
    const marker = parsePipelineFailureMarker(issue.body || '');
    return marker && marker.fingerprint === fingerprint.full;
  });
}

function isSuspendIssue(issue) {
  return Array.isArray(issue && issue.labels) && issue.labels.includes('status::suspend');
}

function typeLabelForAnalysis(analysis) {
  void analysis;
  return 'type::ops';
}

function bullet(value) {
  return value ? `- ${value}` : '- (unknown)';
}

function buildIssueBody(context, analysis, fingerprint, similarClosedIssue) {
  const humanActions = Array.isArray(analysis.humanActions) && analysis.humanActions.length > 0
    ? analysis.humanActions.map(bullet).join('\n')
    : '- None identified.';
  const similar = similarClosedIssue
    ? `\n\n## Similar Closed Issue\n\nA closed issue has the same fingerprint and was left closed by default: #${similarClosedIssue.number} ${similarClosedIssue.htmlUrl || similarClosedIssue.url || ''}`.trimEnd()
    : '';

  return `${buildMarker(context, fingerprint)}

# CI Failure Analysis

## Failure Context

- Run URL: ${context.runUrl || '(unknown)'}
- Commit SHA: ${context.commitSha || '(unknown)'}
- Branch: ${context.branch || '(unknown)'}
- PR/MR: ${context.pullRequest || '(none)'}
- Workflow/Pipeline: ${context.workflowName || '(unknown)'}
- Job: ${context.jobName || '(unknown)'}
- Step: ${context.stepName || '(unknown)'}

## Agent Triage Request

This issue was created automatically from a failed CI run. Do not trust the intake summary as the root cause.

- Intake category: ${analysis.category}
- Intake confidence: ${analysis.confidence}
- Agent task: inspect the linked run, logs, repository, workflow configuration, and related PR/MR to decide whether this is a code regression, workflow/provider configuration issue, transient infrastructure failure, or false positive.
- Signature for dedupe: \`${analysis.normalizedErrorSignature}\`

## Suggested Human Actions

${humanActions}

## Log Summary

\`\`\`text
${analysis.logSummary || '(no concise log summary available)'}
\`\`\`

## Validation

${analysis.validation || 'Re-run the failed CI workflow or pipeline.'}${similar ? `\n\n${similar}` : ''}
`;
}

function buildDuplicateComment(context, analysis, sameRootCause = true) {
  return `CI failure repeated for the same fingerprint.

- Run URL: ${context.runUrl || '(unknown)'}
- Commit SHA: ${context.commitSha || '(unknown)'}
- Branch: ${context.branch || '(unknown)'}
- PR/MR: ${context.pullRequest || '(none)'}
- Same root cause: ${sameRootCause ? 'yes' : 'unknown'}
- Signature: \`${analysis.normalizedErrorSignature}\`
`;
}

function titleForIssue(context) {
  const workflow = context.workflowName || 'CI';
  const job = context.jobName ? ` / ${context.jobName}` : '';
  return `Fix CI failure: ${workflow}${job}`;
}

function readOptionalLogFile(options = {}, env = process.env) {
  const candidate = options.logFile || env.ISSUE_FLOW_FAILURE_LOG_FILE;
  if (!candidate) {
    return '';
  }
  return truncate(fs.readFileSync(candidate, 'utf8'), LOG_LIMIT);
}

function isFailedGithubWorkflowRun(payload) {
  const run = payload.workflow_run || {};
  return run.conclusion === 'failure' || run.conclusion === 'timed_out';
}

function isSelfGithubFailureIntakeRun(payload) {
  const run = payload.workflow_run || {};
  return run.name === FAILURE_INTAKE_WORKFLOW_NAME;
}

function failedStepName(steps = []) {
  const step = steps.find((candidate) => ['failure', 'timed_out', 'cancelled'].includes(String(candidate.conclusion || '').toLowerCase()));
  return step && step.name ? step.name : '';
}

function pullRequestLabelFromGithubRun(run) {
  const pullRequests = Array.isArray(run.pull_requests) ? run.pull_requests : [];
  const pr = pullRequests[0];
  if (!pr) {
    return '';
  }
  return pr.number ? `#${pr.number}` : pr.url || '';
}

async function githubFailureContext(payload, provider, repo, options = {}) {
  const run = payload.workflow_run || {};
  if (!isFailedGithubWorkflowRun(payload)) {
    return { skipped: true, reason: 'github_workflow_run_not_failed' };
  }
  if (isSelfGithubFailureIntakeRun(payload)) {
    return { skipped: true, reason: 'self_failure_intake_workflow' };
  }

  let details = { jobs: [] };
  if (provider.collectWorkflowRunFailureDetails) {
    details = await provider.collectWorkflowRunFailureDetails(repo, run.id, options);
  }
  const failedJobs = Array.isArray(details.jobs) ? details.jobs : [];
  const baseContext = {
    provider: 'github',
    repoFullName: repo.fullName,
    workflowName: run.name || payload.workflow && payload.workflow.name || '',
    jobName: '',
    stepName: '',
    runUrl: run.html_url || '',
    commitSha: run.head_sha || '',
    branch: run.head_branch || '',
    pullRequest: pullRequestLabelFromGithubRun(run),
    log: '',
    raw: payload,
  };
  if (failedJobs.length === 0) {
    return baseContext;
  }

  const contexts = failedJobs.map((job) => ({
    ...baseContext,
    jobName: job.name || '',
    stepName: failedStepName(job.steps || []),
    runUrl: job.htmlUrl || baseContext.runUrl,
    log: truncate(job.log || '', LOG_LIMIT),
  }));
  return contexts[0];
}

function isFailedGitlabPayload(payload) {
  const attrs = payload.object_attributes || {};
  return attrs.status === 'failed' || attrs.status === 'failure' || payload.build_status === 'failed';
}

function gitlabFailedBuild(payload) {
  const builds = Array.isArray(payload.builds) ? payload.builds : [];
  return builds.find((build) => build.status === 'failed') || {};
}

function gitlabFailureContext(payload, repo, options = {}, env = process.env) {
  const attrs = payload.object_attributes || {};
  const build = gitlabFailedBuild(payload);
  const hasPayloadFailure = payload.object_kind === 'pipeline' || payload.object_kind === 'job' || isFailedGitlabPayload(payload);
  const hasEnvFailure =
    env.CI_JOB_STATUS === 'failed' ||
    gitlabBridgeValue(env, 'EVENT_ACTION') === 'failed' ||
    gitlabBridgeValue(env, 'WORKFLOW_RUN_CONCLUSION') === 'failure' ||
    gitlabBridgeValue(env, 'PIPELINE_STATUS') === 'failed';
  if (!hasPayloadFailure && !hasEnvFailure) {
    return { skipped: true, reason: 'gitlab_pipeline_or_job_not_failed' };
  }

  const prNumber = gitlabBridgeValue(env, 'PR_NUMBER');
  return {
    provider: 'gitlab',
    repoFullName: repo.fullName,
    workflowName: attrs.name || attrs.ref || gitlabBridgeValue(env, 'PIPELINE_NAME') || env.CI_PIPELINE_NAME || 'GitLab CI',
    jobName: build.name || attrs.name || gitlabBridgeValue(env, 'JOB_NAME') || env.CI_JOB_NAME || '',
    stepName: env.ISSUE_FLOW_FAILURE_STEP || '',
    runUrl: build.web_url || attrs.url || gitlabBridgeValue(env, 'WORKFLOW_RUN_URL') || env.CI_JOB_URL || env.CI_PIPELINE_URL || '',
    commitSha: attrs.sha || gitlabBridgeValue(env, 'WORKFLOW_RUN_SHA') || env.CI_COMMIT_SHA || '',
    branch: attrs.ref || gitlabBridgeValue(env, 'WORKFLOW_RUN_REF') || env.CI_COMMIT_REF_NAME || '',
    pullRequest: prNumber ? `!${prNumber}` : env.CI_MERGE_REQUEST_IID ? `!${env.CI_MERGE_REQUEST_IID}` : '',
    log: truncate(readOptionalLogFile(options, env) || env.ISSUE_FLOW_FAILURE_LOG || gitlabBridgeValue(env, 'FAILURE_LOG') || '', LOG_LIMIT),
    raw: payload,
  };
}

async function collectFailureContext(options = {}, env = process.env) {
  const loaded = loadEventPayload(options, env);
  const payload = loaded.payload || {};
  const provider = resolveProvider(options, payload);
  const repo = provider.resolveRepo(payload, options);

  if (provider.name === 'github') {
    return githubFailureContext(payload, provider, repo, options);
  }
  return gitlabFailureContext(payload, repo, options, env);
}

function failureLabelDefinitions(hashLabel) {
  return [
    {
      name: FAILURE_LABEL,
      color: 'B60205',
      description: 'CI/CD failure analysis issue',
    },
    {
      name: hashLabel,
      color: 'D73A4A',
      description: 'CI failure fingerprint index',
    },
  ];
}

async function ensureFailureLabels(provider, repo, hashLabel, options = {}) {
  for (const definition of failureLabelDefinitions(hashLabel)) {
    if (provider.ensureLabelDefinition) {
      await provider.ensureLabelDefinition(repo, definition, options);
    }
  }
}

function currentLabels(issue) {
  return Array.isArray(issue && issue.labels) ? issue.labels : [];
}

async function reactivateIssueIfNeeded(provider, issue, options = {}) {
  if (!isSuspendIssue(issue)) {
    return false;
  }
  const desired = {
    status: 'status::active',
    flow: 'flow::build',
    automation: 'automation::build',
  };
  const changes = computeLabelChanges(currentLabels(issue), desired, []);
  await provider.applyLabels(
    { ...issue, issueNumber: issue.number, number: issue.number },
    changes.labelsToAdd,
    changes.labelsToRemove,
    options
  );
  return true;
}

function writeTempBody(prefix, body) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  const file = path.join(dir, 'body.md');
  fs.writeFileSync(file, body, 'utf8');
  return {
    file,
    cleanup: () => fs.rmSync(dir, { recursive: true, force: true }),
  };
}

function providerOptionArgs(options = {}) {
  const args = [];
  if (options.provider) {
    args.push('--provider', options.provider);
  }
  if (options.repo) {
    args.push('--repo', options.repo);
  }
  for (const key of ['gitlabUrl', 'gitlabApiUrl', 'gitlabProject', 'gitlabToken']) {
    if (options[key]) {
      args.push(`--${key.replace(/[A-Z]/g, (char) => `-${char.toLowerCase()}`)}`, options[key]);
    }
  }
  return args;
}

function createIssueCliArgs({ title, bodyFile, type, fingerprint, options }) {
  return [
      path.resolve(__dirname, '..', 'cli.cjs'),
      'issue',
      'create',
      '--title',
      title,
      '--body-file',
      bodyFile,
      '--type',
      type,
      '--status',
      'status::active',
      '--flow',
      'flow::build',
      '--automation',
      'automation::build',
      '--priority',
      ISSUE_PRIORITY,
      '--size',
      'size::M',
      '--label',
      FAILURE_LABEL,
      '--label',
      fingerprint.label,
      ...providerOptionArgs(options),
  ];
}

function createIssueViaCli({ title, body, type, fingerprint, options }) {
  const temp = writeTempBody('issue-flow-pipeline-failed-', body);
  try {
    const args = createIssueCliArgs({ title, bodyFile: temp.file, type, fingerprint, options });
    const child = spawnSync(process.execPath, args, { encoding: 'utf8' });
    if (child.status !== 0) {
      throw new Error((child.stderr || child.stdout || '').trim() || `issue-flow issue create exited with status ${child.status ?? 1}`);
    }
    return parseCreateIssueCliOutput(child.stdout);
  } finally {
    temp.cleanup();
  }
}

function parseCreateIssueCliOutput(stdout) {
  const parsed = JSON.parse(String(stdout || '{}'));
  if (parsed && parsed.data && typeof parsed.data === 'object') {
    return parsed.data;
  }
  if (parsed && typeof parsed.stdout === 'string') {
    return JSON.parse(parsed.stdout || '{}');
  }
  return parsed;
}

async function runFailureIntake(options = {}) {
  const context = await collectFailureContext(options);
  if (context.skipped) {
    return {
      action: 'skipped',
      reason: context.reason,
    };
  }

  const analysis = analyzeFailureContext(context);
  const fingerprint = fingerprintFailure(context, analysis);
  const provider = resolveProvider({ ...options, provider: context.provider }, context.raw || {});
  const repo = provider.resolveRepo(context.raw || {}, options);
  const issueType = typeLabelForAnalysis(analysis);

  if (options.dryRun) {
    return {
      action: 'would_create_or_update',
      dryRun: true,
      context,
      analysis,
      fingerprint,
      labels: [issueType, 'status::active', 'flow::build', 'automation::build', ISSUE_PRIORITY, 'size::M', FAILURE_LABEL, fingerprint.label],
    };
  }

  await ensureFailureLabels(provider, repo, fingerprint.label, options);
  const openCandidates = await provider.listIssuesByLabel(repo, fingerprint.label, { ...options, state: 'open' });
  const existing = findMatchingIssue(openCandidates, fingerprint);
  if (existing) {
    await provider.createIssueComment(existing, buildDuplicateComment(context, analysis, true), options);
    const reactivated = await reactivateIssueIfNeeded(provider, existing, options);
    return {
      action: 'updated',
      issueNumber: existing.number,
      issueUrl: existing.htmlUrl,
      reactivated,
      fingerprint,
    };
  }

  const closedCandidates = await provider.listIssuesByLabel(repo, fingerprint.label, { ...options, state: 'closed' });
  const similarClosedIssue = findMatchingIssue(closedCandidates, fingerprint);
  const openCandidatesBeforeCreate = await provider.listIssuesByLabel(repo, fingerprint.label, { ...options, state: 'open' });
  const duplicateBeforeCreate = findMatchingIssue(openCandidatesBeforeCreate, fingerprint);
  if (duplicateBeforeCreate) {
    await provider.createIssueComment(duplicateBeforeCreate, buildDuplicateComment(context, analysis, true), options);
    const reactivated = await reactivateIssueIfNeeded(provider, duplicateBeforeCreate, options);
    return {
      action: 'updated',
      issueNumber: duplicateBeforeCreate.number,
      issueUrl: duplicateBeforeCreate.htmlUrl,
      reactivated,
      fingerprint,
    };
  }

  const body = buildIssueBody(context, analysis, fingerprint, similarClosedIssue);
  const created = createIssueViaCli({
    title: titleForIssue(context),
    body,
    type: issueType,
    fingerprint,
    options: { ...options, provider: context.provider, repo: options.repo || repo.fullName },
  });

  return {
    action: 'created',
    issue: created,
    issueNumber: created.issueNumber || created.number,
    issueUrl: created.issueUrl || created.htmlUrl,
    fingerprint,
    labels: created.labels,
    similarClosedIssue: similarClosedIssue ? similarClosedIssue.number : undefined,
  };
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }
  const result = await runFailureIntake(options);
  console.log(JSON.stringify(result, null, 2));
  return 0;
}

module.exports = {
  FAILURE_LABEL,
  FAILURE_INTAKE_WORKFLOW_NAME,
  HASH_LABEL_PREFIX,
  MARKER_NAME,
  analyzeFailureContext,
  buildDuplicateComment,
  buildFingerprintInput,
  buildIssueBody,
  buildMarker,
  collectFailureContext,
  createIssueCliArgs,
  findMatchingIssue,
  fingerprintFailure,
  githubFailureContext,
  gitlabFailureContext,
  parseArgs,
  parsePipelineFailureMarker,
  parseCreateIssueCliOutput,
  runFailureIntake,
  typeLabelForAnalysis,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
