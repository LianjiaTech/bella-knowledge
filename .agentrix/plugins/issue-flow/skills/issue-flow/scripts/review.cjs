#!/usr/bin/env node

const fs = require('node:fs');
const childProcess = require('node:child_process');
const { loadEventPayload } = require('./events.cjs');
const { providers, resolveProvider } = require('./providers.cjs');
const { buildSourceMarker } = require('./provenance.cjs');

const VALUE_OPTIONS = new Set([
  '--event',
  '--provider',
  '--repo',
  '--pr-number',
  '--body-file',
  '--comments-file',
  '--commit-id',
  '--gitlab-url',
  '--gitlab-api-url',
  '--gitlab-project',
  '--gitlab-token',
]);

const BOOLEAN_OPTIONS = new Set([
  '--as-comment',
  '--dry-run',
  '--help',
]);

function usage() {
  return [
    'Usage: review.cjs --pr-number <num> --body-file <path> [options]',
    '',
    'Submits a PR/MR review result. GitHub uses the Pull Request Review API; GitLab posts an MR note.',
    '',
    'Options:',
    '  --event <path>          Event JSON path. Defaults to GITHUB_EVENT_PATH or GITLAB_EVENT_PATH.',
    '  --provider <provider>   Git hosting provider: github or gitlab. Defaults from event/environment.',
    '  --repo <owner/repo>     Repository/project override.',
    '  --pr-number <num>       PR/MR number.',
    '  --body-file <path>      Markdown review body.',
    '  --comments-file <path>  JSON array of inline review comments.',
    '  --commit-id <sha>       Commit SHA for GitHub review submission. Defaults to PR head SHA.',
    '  --as-comment            Post the body as a normal PR/MR comment. Cannot include inline comments.',
    '  --dry-run',
    '  --help',
  ].join('\n');
}

function parseArgs(argv) {
  const options = {
    _: [],
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (BOOLEAN_OPTIONS.has(arg)) {
      const key = arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
      options[key] = true;
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

function loadPayload(options = {}) {
  const loaded = loadEventPayload(options);
  return loaded.source === 'empty' ? {} : loaded.payload;
}

function readBodyFile(bodyFile) {
  if (!bodyFile) {
    throw new Error('--body-file is required');
  }
  return fs.readFileSync(bodyFile, 'utf8').trim();
}

function readReviewCommentsFile(commentsFile) {
  if (!commentsFile) {
    return [];
  }
  const parsed = JSON.parse(fs.readFileSync(commentsFile, 'utf8'));
  if (!Array.isArray(parsed)) {
    throw new Error('--comments-file must contain a JSON array');
  }
  for (const [index, comment] of parsed.entries()) {
    if (!comment || typeof comment !== 'object' || Array.isArray(comment)) {
      throw new Error(`review comment at index ${index} must be an object`);
    }
    if (typeof comment.body !== 'string' || comment.body.trim() === '') {
      throw new Error(`review comment at index ${index} must include body`);
    }
    if (typeof comment.path !== 'string' || comment.path.trim() === '') {
      throw new Error(`review comment at index ${index} must include path`);
    }
  }
  return parsed;
}

async function buildReviewPullRequest(options = {}) {
  const payload = loadPayload(options);
  const provider = resolveProvider(options, payload);
  const pr = provider.buildPullRequestContext(payload, options);
  if (options.dryRun) {
    return { provider, pr };
  }
  return {
    provider,
    pr: await provider.fetchCurrentPullRequest(pr, options),
  };
}

function resolveCurrentCheckoutHead(options = {}) {
  if (options.dryRun) {
    return '';
  }
  const result = childProcess.spawnSync('git', ['rev-parse', 'HEAD'], {
    encoding: 'utf8',
  });
  if (result.error || result.status !== 0) {
    return '';
  }
  return String(result.stdout || '').trim();
}

function buildStaleHeadResult(provider, pr, comments, checkoutHead) {
  return {
    action: 'skipped',
    reason: 'stale_head',
    provider: provider.name,
    pullRequest: pr.number,
    checkoutHead,
    currentHead: pr.headSha || '',
    inlineComments: comments.length,
  };
}

function normalizeMarkerValue(value) {
  return String(value || '').trim().replace(/[^\w:./-]+/g, '-');
}

function resolveAgentrixTaskId() {
  return String(process.env.AGENTRIX_TASK_ID || '').trim();
}

function buildReviewMetadataMarker(options = {}) {
  const fields = [];
  const taskId = normalizeMarkerValue(options.taskId);
  const headSha = normalizeMarkerValue(options.headSha);
  if (taskId) {
    fields.push(`task=${taskId}`);
  }
  if (headSha) {
    fields.push(`head=${headSha}`);
  }
  if (fields.length === 0) {
    return '';
  }
  return `<!-- issue-flow:review ${fields.join(' ')} -->`;
}

function appendReviewMetadata(body, options = {}) {
  const marker = buildReviewMetadataMarker(options);
  const sourceMarker = buildSourceMarker({ sourceTaskId: options.taskId, sourceRuntime: 'agentrix' });
  const markers = [marker, sourceMarker].filter(Boolean);
  if (markers.length === 0) {
    return body;
  }
  return `${body.trim()}\n\n${markers.join('\n')}`;
}

function appendInlineReviewMetadata(comments = [], options = {}) {
  const sourceMarker = buildSourceMarker({ sourceTaskId: options.taskId, sourceRuntime: 'agentrix' });
  if (!sourceMarker) {
    return comments;
  }
  return comments.map((comment, index) => index === 0
    ? comment
    : { ...comment, body: `${comment.body.trim()}\n\n${sourceMarker}` });
}

async function submitReview(options = {}) {
  const body = readBodyFile(options.bodyFile);
  const comments = readReviewCommentsFile(options.commentsFile);
  if (options.asComment && comments.length > 0) {
    throw new Error('--as-comment cannot include inline review comments');
  }
  const { provider, pr } = await buildReviewPullRequest(options);
  const checkoutHead = resolveCurrentCheckoutHead(options);
  if (!options.dryRun && (!checkoutHead || !pr.headSha || checkoutHead !== pr.headSha)) {
    return buildStaleHeadResult(provider, pr, comments, checkoutHead);
  }
  const reviewBody = appendReviewMetadata(body, {
    taskId: resolveAgentrixTaskId(),
    headSha: pr.headSha,
  });
  if (options.asComment) {
    const comment = await provider.createPullRequestComment(pr, reviewBody, options);
    return {
      action: 'commented',
      provider: provider.name,
      pullRequest: pr.number,
      commentUrl: comment && (comment.html_url || comment.htmlUrl || comment.web_url || ''),
      inlineComments: 0,
    };
  }
  const reviewComments = appendInlineReviewMetadata(comments, {
    taskId: resolveAgentrixTaskId(),
  });
  const review = await provider.submitPullRequestReview(pr, reviewBody, options, reviewComments);
  return {
    action: 'submitted',
    provider: provider.name,
    pullRequest: pr.number,
    reviewUrl: review && (review.html_url || review.htmlUrl || review.web_url || ''),
    inlineComments: comments.length,
  };
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }
  const result = await submitReview(options);
  console.log(JSON.stringify(result, null, 2));
  return 0;
}

module.exports = {
  appendInlineReviewMetadata,
  appendReviewMetadata,
  buildReviewPullRequest,
  buildReviewMetadataMarker,
  resolveCurrentCheckoutHead,
  main,
  parseArgs,
  readReviewCommentsFile,
  submitReview,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
