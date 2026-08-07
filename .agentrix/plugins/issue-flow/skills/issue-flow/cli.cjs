#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { resolveProviderPort } = require('./scripts/providers.cjs');
const { upsertSourceMarker } = require('./scripts/provenance.cjs');

const SCRIPT_DIR = path.join(__dirname, 'scripts');
const BOOLEAN_OPTIONS = new Set([
  '--dry-run',
  '--draft',
  '--as-comment',
  '--no-push',
  '--clear-flow',
  '--clear-automation',
  '--check',
  '--help',
]);

function topHelp() {
  return [
    'Usage: issue-flow <resource> <action> [options]',
    '',
    'Resources:',
    '  issue      Get, create, apply labels, intake, comments, and acknowledge issues.',
    '  pr         Get, submit, comment, review, and handle merged PRs/MRs.',
    '  labels     Sync or check managed provider labels.',
    '  milestone  List target milestones or sync them from branch events.',
    '  dispatch   Run issue-flow runtime dispatch actions.',
    '',
    'Examples:',
    '  issue-flow issue get --issue 123',
    '  issue-flow issue apply --issue 123 --flow flow::build --size size::M',
    '  issue-flow pr submit build --issue 123 --title "Build #123: Change" --body-file /tmp/body.md',
    '',
    'Common options:',
    '  --provider github|gitlab',
    '  --repo owner/repo|group/project',
    '  --event <path>',
    '  --dry-run',
  ].join('\n');
}

function issueHelp() {
  return [
    'Usage: issue-flow issue <action> [options]',
    '',
    'Actions:',
    '  get --issue <num>',
    '  create --title <title> --body-file <path> [label options]',
    '  apply --issue <num> [label/body options]',
    '  intake --issue <num>',
    '  comments list --issue <num>',
    '  comments create --issue <num> --body-file <path>',
    '  comments update --issue <num> --comment-id <id> --body-file <path>',
    '  comments delete --issue <num> --comment-id <id>',
    '  acknowledge --issue <num> [--content eyes]',
    '  reaction create --issue <num> [--content eyes]',
  ].join('\n');
}

function issueCommentsHelp() {
  return [
    'Usage: issue-flow issue comments <action> [options]',
    '',
    'Actions:',
    '  list --issue <num>',
    '  create --issue <num> --body-file <path>',
    '  update --issue <num> --comment-id <id> --body-file <path>',
    '  delete --issue <num> --comment-id <id>',
  ].join('\n');
}

function prHelp() {
  return [
    'Usage: issue-flow pr <action> [options]',
    '',
    'Actions:',
    '  get --pr <num>',
    '  submit plan|build --issue <num> --title <title> --body-file <path>',
    '  comments list --pr <num>',
    '  comments create --pr <num> --body-file <path>',
    '  comments update --pr <num> --comment-id <id> --body-file <path>',
    '  comments delete --pr <num> --comment-id <id>',
    '  review-comments list --pr <num>',
    '  review --pr <num> --body-file <path> [--comments-file <path>] [--as-comment]',
    '  merged --event <path>',
    '',
    'Note: pr means GitHub PR or GitLab MR.',
  ].join('\n');
}

function prSubmitHelp() {
  return [
    'Usage: issue-flow pr submit <plan|build> --issue <num> --title <title> --body-file <path> [options]',
    '',
    'Options:',
    '  --agentrix-task-id <id>',
    '  --base <branch>',
    '  --head <branch>',
    '  --draft',
    '  --no-push',
    '  --dry-run',
  ].join('\n');
}

function prCommentsHelp() {
  return [
    'Usage: issue-flow pr comments <action> [options]',
    '',
    'Actions:',
    '  list --pr <num>',
    '  create --pr <num> --body-file <path>',
    '  update --pr <num> --comment-id <id> --body-file <path>',
    '  delete --pr <num> --comment-id <id>',
  ].join('\n');
}

function prReviewCommentsHelp() {
  return [
    'Usage: issue-flow pr review-comments <action> [options]',
    '',
    'Actions:',
    '  list --pr <num>',
  ].join('\n');
}

function labelsHelp() {
  return [
    'Usage: issue-flow labels <sync|check> [options]',
    '',
    'Actions:',
    '  sync       Upsert managed labels.',
    '  check      Check managed labels and fail on drift.',
  ].join('\n');
}

function milestoneHelp() {
  return [
    'Usage: issue-flow milestone <list|sync> [options]',
    '',
    'Actions:',
    '  list       List selectable target milestones.',
    '  sync       Handle a branch create/delete event.',
  ].join('\n');
}

function dispatchHelp() {
  return [
    'Usage: issue-flow dispatch <action> [options]',
    '',
    'Actions:',
    '  auto | comment | review | review-comment | pr-merged | pipeline-failed | resume | triage | plan | build | general',
  ].join('\n');
}

function printJson(payload) {
  process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
}

function parseOptions(argv) {
  const options = { _: [] };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith('--')) {
      options._.push(arg);
      continue;
    }
    const key = arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
    if (BOOLEAN_OPTIONS.has(arg)) {
      options[key] = true;
      continue;
    }
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for ${arg}`);
    }
    options[key] = value;
    index += 1;
  }
  return options;
}

function optionsToArgs(options) {
  const args = [];
  for (const [key, value] of Object.entries(options)) {
    if (key === '_') {
      args.push(...value);
      continue;
    }
    const flag = `--${key.replace(/[A-Z]/g, (char) => `-${char.toLowerCase()}`)}`;
    if (value === true) {
      args.push(flag);
    } else if (value !== false && value !== undefined) {
      args.push(flag, String(value));
    }
  }
  return args;
}

function mapAliasArgs(argv) {
  const mapped = [];
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--issue') {
      mapped.push('--issue-number', argv[index + 1]);
      index += 1;
      continue;
    }
    if (arg === '--pr') {
      mapped.push('--pr-number', argv[index + 1]);
      index += 1;
      continue;
    }
    mapped.push(arg);
  }
  return mapped;
}

function parseJsonOutput(stdout) {
  const trimmed = String(stdout || '').trim();
  if (!trimmed) {
    return undefined;
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return undefined;
  }
}

function runScript(scriptName, argv, metadata = {}) {
  const scriptPath = path.join(SCRIPT_DIR, scriptName);
  const child = spawnSync(process.execPath, [scriptPath, ...argv], {
    encoding: 'utf8',
  });
  if (child.status !== 0) {
    const error = new Error((child.stderr || child.stdout || '').trim() || `${scriptName} exited with status ${child.status ?? 1}`);
    error.status = child.status || 1;
    throw error;
  }

  const stdout = String(child.stdout || '').trim();
  const stderr = String(child.stderr || '').trim();
  const parsed = parseJsonOutput(stdout);
  return {
    action: metadata.action || 'routed',
    resource: metadata.resource,
    command: metadata.command,
    script: scriptName,
    data: parsed,
    stdout: parsed ? undefined : stdout || undefined,
    stderr: stderr || undefined,
  };
}

function readBodyFile(bodyFile) {
  if (!bodyFile) {
    throw new Error('--body-file is required');
  }
  return fs.readFileSync(bodyFile, 'utf8').trim();
}

function readMarkedCommentBody(bodyFile) {
  return upsertSourceMarker(readBodyFile(bodyFile));
}

async function withoutConsoleLog(callback) {
  const originalLog = console.log;
  const lines = [];
  console.log = (line) => lines.push(String(line));
  try {
    const result = await callback();
    return { result, capturedStdout: lines.join('\n') };
  } finally {
    console.log = originalLog;
  }
}

function providerOptions(options) {
  return {
    ...options,
    issueNumber: options.issueNumber || options.issue,
    prNumber: options.prNumber || options.pr,
    quiet: true,
  };
}

async function withPort(options, callback) {
  const port = resolveProviderPort(providerOptions(options), {});
  return withoutConsoleLog(() => callback(port));
}

function baseEnvelope(action, provider, resource, options, extra = {}) {
  return {
    action,
    provider,
    resource,
    issue: options.issue ? Number(options.issue) : undefined,
    pr: options.pr ? Number(options.pr) : undefined,
    dryRun: Boolean(options.dryRun),
    ...extra,
  };
}

async function handleIssue(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return issueHelp();
  }
  const action = argv[0];
  if (action === 'get') {
    const options = parseOptions(argv.slice(1));
    const { result } = await withPort(options, (port) => port.issues.get());
    return baseEnvelope('fetched', result.provider, 'issue', options, {
      repo: result.repo,
      issue: result.number,
      data: result,
    });
  }
  if (action === 'create') {
    return runScript('create-issue.cjs', mapAliasArgs(argv.slice(1)), { action: 'created', resource: 'issue', command: 'create' });
  }
  if (action === 'apply') {
    return runScript('apply.cjs', mapAliasArgs(argv.slice(1)), { action: 'applied', resource: 'issue', command: 'apply' });
  }
  if (action === 'intake') {
    return runScript('intake.cjs', mapAliasArgs(argv.slice(1)), { action: 'intake', resource: 'issue', command: 'intake' });
  }
  if (action === 'comments') {
    return handleIssueComments(argv.slice(1));
  }
  if (action === 'acknowledge') {
    const options = parseOptions(argv.slice(1));
    const { result } = await withPort(options, (port) => port.issues.acknowledge({ content: options.content }));
    return baseEnvelope('acknowledged', resolveProviderPort(providerOptions(options), {}).provider, 'issue', options, result);
  }
  if (action === 'reaction' && argv[1] === 'create') {
    const options = parseOptions(argv.slice(2));
    const { result } = await withPort(options, (port) => port.issues.createReaction({ content: options.content }));
    return baseEnvelope('created', resolveProviderPort(providerOptions(options), {}).provider, 'issue_reaction', options, result);
  }
  throw new Error(`Unknown issue action: ${action}\n\n${issueHelp()}`);
}

async function handleIssueComments(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return issueCommentsHelp();
  }
  const action = argv[0];
  const options = parseOptions(argv.slice(1));
  if (action === 'list') {
    const { result } = await withPort(options, (port) => port.issues.listComments());
    return baseEnvelope('listed', resolveProviderPort(providerOptions(options), {}).provider, 'issue_comment', options, {
      items: result,
    });
  }
  if (action === 'create') {
    const body = readMarkedCommentBody(options.bodyFile);
    const { result } = await withPort(options, (port) => port.issues.createComment({ body }));
    return baseEnvelope('created', resolveProviderPort(providerOptions(options), {}).provider, 'issue_comment', options, result);
  }
  if (action === 'update') {
    const body = readMarkedCommentBody(options.bodyFile);
    const { result } = await withPort(options, (port) => port.issues.updateComment({ commentId: options.commentId }, { body }));
    return baseEnvelope('updated', resolveProviderPort(providerOptions(options), {}).provider, 'issue_comment', options, result);
  }
  if (action === 'delete') {
    const { result } = await withPort(options, (port) => port.issues.deleteComment({ commentId: options.commentId }));
    return baseEnvelope('deleted', resolveProviderPort(providerOptions(options), {}).provider, 'issue_comment', options, result);
  }
  throw new Error(`Unknown issue comments action: ${action}\n\n${issueCommentsHelp()}`);
}

async function handlePr(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return prHelp();
  }
  const action = argv[0];
  if (action === 'get') {
    const options = parseOptions(argv.slice(1));
    const { result } = await withPort(options, (port) => port.pullRequests.get());
    return baseEnvelope('fetched', result.provider, 'pr', options, {
      repo: result.repo,
      pr: result.number,
      data: result,
    });
  }
  if (action === 'submit') {
    if (argv[1] === '--help' || !argv[1]) {
      return prSubmitHelp();
    }
    return runScript('submit.cjs', [argv[1], ...mapAliasArgs(argv.slice(2))], { action: 'submitted', resource: 'pr', command: `submit ${argv[1]}` });
  }
  if (action === 'comments') {
    return handlePrComments(argv.slice(1));
  }
  if (action === 'review-comments') {
    return handlePrReviewComments(argv.slice(1));
  }
  if (action === 'review') {
    return runScript('review.cjs', mapAliasArgs(argv.slice(1)), { action: 'reviewed', resource: 'pr', command: 'review' });
  }
  if (action === 'merged') {
    return runScript('dispatch.cjs', ['pr-merged', ...mapAliasArgs(argv.slice(1))], { action: 'merged', resource: 'pr', command: 'merged' });
  }
  throw new Error(`Unknown pr action: ${action}\n\n${prHelp()}`);
}

async function handlePrComments(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return prCommentsHelp();
  }
  const action = argv[0];
  const options = parseOptions(argv.slice(1));
  if (action === 'list') {
    const { result } = await withPort(options, (port) => port.pullRequests.listComments());
    return baseEnvelope('listed', resolveProviderPort(providerOptions(options), {}).provider, 'pr_comment', options, {
      items: result,
    });
  }
  if (action === 'create') {
    const body = readMarkedCommentBody(options.bodyFile);
    const { result } = await withPort(options, (port) => port.pullRequests.createComment({ body }));
    return baseEnvelope('created', resolveProviderPort(providerOptions(options), {}).provider, 'pr_comment', options, result);
  }
  if (action === 'update') {
    const body = readMarkedCommentBody(options.bodyFile);
    const { result } = await withPort(options, (port) => port.pullRequests.updateComment({ commentId: options.commentId }, { body }));
    return baseEnvelope('updated', resolveProviderPort(providerOptions(options), {}).provider, 'pr_comment', options, result);
  }
  if (action === 'delete') {
    const { result } = await withPort(options, (port) => port.pullRequests.deleteComment({ commentId: options.commentId }));
    return baseEnvelope('deleted', resolveProviderPort(providerOptions(options), {}).provider, 'pr_comment', options, result);
  }
  throw new Error(`Unknown pr comments action: ${action}\n\n${prCommentsHelp()}`);
}

async function handlePrReviewComments(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return prReviewCommentsHelp();
  }
  const action = argv[0];
  const options = parseOptions(argv.slice(1));
  if (action === 'list') {
    const { result } = await withPort(options, (port) => port.pullRequests.listReviewComments());
    return baseEnvelope('listed', resolveProviderPort(providerOptions(options), {}).provider, 'pr_review_comment', options, {
      items: result,
    });
  }
  throw new Error(`Unknown pr review-comments action: ${action}\n\n${prReviewCommentsHelp()}`);
}

function handleLabels(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return labelsHelp();
  }
  if (argv[0] === 'sync') {
    return runScript('sync-labels.cjs', argv.slice(1), { action: 'synced', resource: 'labels', command: 'sync' });
  }
  if (argv[0] === 'check') {
    return runScript('sync-labels.cjs', ['--check', ...argv.slice(1)], { action: 'checked', resource: 'labels', command: 'check' });
  }
  throw new Error(`Unknown labels action: ${argv[0]}\n\n${labelsHelp()}`);
}

function handleMilestone(argv) {
  if (argv.length === 0 || argv[0] === '--help') return milestoneHelp();
  if (argv[0] === 'list' || argv[0] === 'sync') {
    return runScript('milestone.cjs', [argv[0], ...argv.slice(1)], {
      action: argv[0] === 'list' ? 'listed' : 'synced',
      resource: 'milestone',
      command: argv[0],
    });
  }
  throw new Error(`Unknown milestone action: ${argv[0]}\n\n${milestoneHelp()}`);
}

function handleDispatch(argv) {
  if (argv.length === 0 || argv[0] === '--help') {
    return dispatchHelp();
  }
  if (argv[0] === 'review-comment') {
    const options = parseOptions(mapAliasArgs(argv.slice(1)));
    return withoutConsoleLog(async () => {
      const dispatch = require('./scripts/dispatch.cjs');
      const data = await dispatch.runReviewComment(options);
      return {
        action: 'dispatched',
        resource: 'dispatch',
        command: 'review-comment',
        data,
      };
    }).then(({ result }) => result);
  }
  return runScript('dispatch.cjs', [argv[0], ...mapAliasArgs(argv.slice(1))], {
    action: 'dispatched',
    resource: 'dispatch',
    command: argv[0],
  });
}

async function run(argv = process.argv.slice(2)) {
  if (argv.length === 0 || argv[0] === '--help') {
    return topHelp();
  }
  const resource = argv[0];
  if (resource === 'issue') {
    return handleIssue(argv.slice(1));
  }
  if (resource === 'pr') {
    return handlePr(argv.slice(1));
  }
  if (resource === 'labels') {
    return handleLabels(argv.slice(1));
  }
  if (resource === 'milestone') {
    return handleMilestone(argv.slice(1));
  }
  if (resource === 'dispatch') {
    return handleDispatch(argv.slice(1));
  }
  throw new Error(`Unknown resource: ${resource}\n\n${topHelp()}`);
}

async function main(argv = process.argv.slice(2)) {
  const result = await run(argv);
  if (typeof result === 'string') {
    process.stdout.write(`${result}\n`);
    return 0;
  }
  printJson(result);
  return 0;
}

module.exports = {
  main,
  parseOptions,
  run,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = error && error.status ? error.status : 1;
  });
}
