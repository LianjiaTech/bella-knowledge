#!/usr/bin/env node

const { providers, resolveProvider } = require('./providers.cjs');
const { loadEventPayload } = require('./events.cjs');
const {
  normalizeLabels,
  resolveAutomationDecision: resolveCoreAutomationDecision,
  resolveResumeDecision,
  shouldRunAutoForEvent,
} = require('./resolve.cjs');
const { parseSourceMarker } = require('./provenance.cjs');
const prMerged = require('./pr-merged.cjs');
const pipelineFailed = require('./pipeline-failed.cjs');
const { completeOptimizationForChildIssue } = require('./optimization-completion.cjs');
const { resolveIssueTarget } = require('./milestones.cjs');

const DEFAULT_RUNTIME = 'agentrix';
const TRIGGER_COMMENT_REACTION = 'eyes';
const VALUE_OPTIONS = new Set([
  '--event',
  '--provider',
  '--repo',
  '--runtime',
  '--issue-number',
  '--pr-number',
  '--instruction',
  '--auto-default',
  '--review-enabled',
  '--config',
  '--prompts-dir',
  '--templates-dir',
  '--plan-root-dir',
  '--gitlab-url',
  '--gitlab-api-url',
  '--gitlab-project',
  '--gitlab-token',
  '--log-file',
]);

function logIssueFlow(message, details = {}) {
  const entries = Object.entries(details)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${key}=${String(value)}`);
  console.log(`[issue-flow] ${message}${entries.length > 0 ? ` ${entries.join(' ')}` : ''}`);
}

function usage() {
  return [
    'Usage: dispatch.cjs <command> [options]',
    '',
    'Commands:',
    '  auto       Run the current flow:: action when automation policy allows it',
    '  comment    Route an issue comment for the selected runtime mention',
    '  review     Run an independent PR/MR automatic review check',
    '  review-comment  Resume the PR/MR task for a new review comment',
    '  pr-merged  Apply merged plan/build PR transition',
    '  pipeline-failed  Create or update a build issue for a failed CI pipeline/job',
    '  resume     Run the action selected by the current flow:: label',
    '  general    Start a broad runtime action from a manual instruction',
    '  triage     Start triage action',
    '  plan       Start plan action',
    '  build      Start build action',
    '',
    'Common options:',
    '  --event <path>          Event JSON path. Defaults to GITHUB_EVENT_PATH or GITLAB_EVENT_PATH.',
    '  --provider <provider>   Git hosting provider: github or gitlab. Defaults from event/environment.',
    '  --repo <owner/repo>     Repository/project override.',
    '  --runtime <name>        Runtime preset. Defaults to agentrix.',
    '  --issue-number <num>    Issue number override.',
    '  --pr-number <num>       PR/MR number for manual review dispatch.',
    '  --log-file <path>       Failed job log file for pipeline-failed.',
    '  --instruction <text>    Manual instruction for the general command.',
    '  --auto-default <level>  Repository default automation level: off, triage, plan, or build.',
    '  --review-enabled <bool> Enable review when true or 1. Defaults to ISSUE_FLOW_REVIEW_ENABLED.',
    '  --config <path>         Runtime config path.',
    '  --prompts-dir <path>    Agentrix prompt override directory.',
    '  --templates-dir <path>  Agentrix template override directory.',
    '  --plan-root-dir <path>  Agentrix plan root directory.',
    '  --dry-run              Print intended behavior without calling external APIs.',
    '  --help',
  ].join('\n');
}

function parseArgs(argv) {
  if (argv[0] === '--help') {
    return {
      command: undefined,
      options: {
        _: [],
        help: true,
      },
    };
  }

  const command = argv[0];
  const options = {
    _: [],
  };

  for (let index = 1; index < argv.length; index += 1) {
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

  return { command, options };
}

function loadEvent(options = {}) {
  const loaded = loadEventPayload(options);
  if (loaded.source === 'empty') {
    logIssueFlow('No event path provided; using empty payload');
    return {};
  }

  if (loaded.source === 'file') {
    logIssueFlow('Loading event payload', { eventPath: loaded.eventPath });
  } else {
    logIssueFlow('Loading event payload from Agentrix GitLab bridge');
  }

  const payload = loaded.payload;
  const provider = resolveProvider(options, payload);
  logIssueFlow('Loaded event payload', {
    provider: provider.name,
    event: process.env[provider.envEventName] || process.env.GITHUB_EVENT_NAME || process.env.GITLAB_EVENT_NAME,
    action: payload.action,
  });
  return payload;
}

function loadRuntime(options = {}) {
  const runtimeName = options.runtime || process.env.ISSUE_FLOW_RUNTIME || DEFAULT_RUNTIME;
  if (runtimeName !== 'agentrix') {
    throw new Error(`Unsupported issue-flow runtime: ${runtimeName}`);
  }
  return require('./runtimes/agentrix.cjs');
}

function buildIssueContext(payload, options = {}) {
  return resolveProvider(options, payload).buildIssueContext(payload, options);
}

function buildPullRequestContext(payload, options = {}) {
  return resolveProvider(options, payload).buildPullRequestContext(payload, options);
}

function isPullRequestIssue(payload, options = {}) {
  return resolveProvider(options, payload).isPullRequestIssue(payload);
}

function isBotComment(payload, options = {}) {
  return resolveProvider(options, payload).isBotComment(payload);
}

function getCommentContext(payload, options = {}) {
  return resolveProvider(options, payload).getCommentContext(payload);
}

function isReviewCommentCreatedEvent(payload, options = {}) {
  const provider = resolveProvider(options, payload);
  return provider.isReviewCommentCreatedEvent
    ? provider.isReviewCommentCreatedEvent(payload, options)
    : { ok: false, reason: 'not_pull_request_review_comment' };
}

function getReviewCommentContext(payload, options = {}) {
  const provider = resolveProvider(options, payload);
  return provider.getReviewCommentContext
    ? provider.getReviewCommentContext(payload, options)
    : {};
}

async function fetchCurrentIssue(issue, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  if (options.dryRun || !provider.hasToken(options)) {
    logIssueFlow('Using payload issue state', {
      issue: `#${issue.number}`,
      dryRun: Boolean(options.dryRun),
      provider: provider.name,
      hasToken: Boolean(provider.hasToken(options)),
    });
    return issue;
  }

  logIssueFlow('Fetching current issue state', { issue: `#${issue.number}`, provider: provider.name });
  const currentIssue = await provider.fetchCurrentIssue(issue, options);
  logIssueFlow('Fetched current issue state', {
    issue: `#${currentIssue.number}`,
    state: currentIssue.state,
    labels: currentIssue.labels.join(',') || '(none)',
  });
  return currentIssue;
}

async function fetchCurrentPullRequest(pr, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  const shouldFetch = Boolean(options.prNumber) || (!options.dryRun && provider.hasToken(options));
  if (!shouldFetch) {
    logIssueFlow('Using payload PR/MR state', {
      pr: `#${pr.number}`,
      dryRun: Boolean(options.dryRun),
      provider: provider.name,
      hasToken: Boolean(provider.hasToken(options)),
    });
    return pr;
  }

  logIssueFlow('Fetching current PR/MR state', { pr: `#${pr.number}`, provider: provider.name });
  const currentPr = await provider.fetchCurrentPullRequest(pr, options);
  logIssueFlow('Fetched current PR/MR state', {
    pr: `#${currentPr.number}`,
    state: currentPr.state,
    draft: Boolean(currentPr.draft),
    merged: Boolean(currentPr.merged),
    labels: currentPr.labels.join(',') || '(none)',
  });
  return currentPr;
}

async function listIssueComments(issue, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  return provider.listIssueComments(issue, options);
}

async function listPullRequestComments(pr, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  return provider.listPullRequestComments(pr, options);
}

async function createIssueComment(issue, body, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  return provider.createIssueComment(issue, body, options);
}

async function createPullRequestComment(pr, body, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  return provider.createPullRequestComment(pr, body, options);
}

async function updateIssueComment(issue, commentId, body, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  await provider.updateIssueComment(issue, commentId, body, options);
}

async function updatePullRequestComment(pr, commentId, body, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  await provider.updatePullRequestComment(pr, commentId, body, options);
}

async function deleteIssueComment(issue, commentId, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  await provider.deleteIssueComment(issue, commentId, options);
}

async function deletePullRequestComment(pr, commentId, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  await provider.deletePullRequestComment(pr, commentId, options);
}

async function addTriggerCommentReaction(issue, comment, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  await provider.addTriggerCommentReaction(issue, comment, TRIGGER_COMMENT_REACTION, options);
}

async function addReviewCommentReaction(pr, comment, options = {}) {
  const provider = providers[pr.provider] || resolveProvider(options);
  await provider.addReviewCommentReaction(pr, comment, TRIGGER_COMMENT_REACTION, options);
}

async function addIssueReaction(issue, options = {}) {
  const provider = providers[issue.provider] || resolveProvider(options);
  await provider.addIssueReaction(issue, TRIGGER_COMMENT_REACTION, options);
}

function findActionTaskComment(comments, action, runtime, data = {}) {
  const marker = runtime.buildTaskCommentMarker(action, data);
  if (!Array.isArray(comments)) {
    return undefined;
  }
  return comments.find((comment) => typeof comment.body === 'string' && comment.body.includes(marker));
}

function normalizeCommentId(comment) {
  return comment && comment.id !== undefined ? String(comment.id) : '';
}

async function claimActionTask(issue, action, runtime, data, options = {}) {
  const existing = findActionTaskComment(await listIssueComments(issue, options), action, runtime);
  if (existing) {
    return {
      claimed: false,
      comment: existing,
    };
  }

  const created = await createIssueComment(issue, runtime.buildTaskComment(action, { status: 'starting' }, data), options);
  if (options.dryRun) {
    return {
      claimed: true,
      comment: created,
    };
  }

  const winner = findActionTaskComment(await listIssueComments(issue, options), action, runtime);
  if (winner && normalizeCommentId(winner) !== normalizeCommentId(created)) {
    try {
      await deleteIssueComment(issue, created.id, options);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      console.warn(`Unable to delete duplicate issue-flow task lock comment; continuing. ${detail}`);
    }
    return {
      claimed: false,
      comment: winner,
    };
  }

  return {
    claimed: true,
    comment: created,
  };
}

async function claimPullRequestActionTask(pr, action, runtime, data, options = {}) {
  const existing = findActionTaskComment(await listPullRequestComments(pr, options), action, runtime, data);
  if (existing) {
    return {
      claimed: false,
      comment: existing,
    };
  }

  const created = await createPullRequestComment(pr, runtime.buildTaskComment(action, { status: 'starting' }, data), options);
  if (options.dryRun) {
    return {
      claimed: true,
      comment: created,
    };
  }

  const winner = findActionTaskComment(await listPullRequestComments(pr, options), action, runtime, data);
  if (winner && normalizeCommentId(winner) !== normalizeCommentId(created)) {
    try {
      await deletePullRequestComment(pr, created.id, options);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      console.warn(`Unable to delete duplicate issue-flow PR/MR task lock comment; continuing. ${detail}`);
    }
    return {
      claimed: false,
      comment: winner,
    };
  }

  return {
    claimed: true,
    comment: created,
  };
}

async function acknowledgeAutoIssue(issue, action, runtime, data, options = {}) {
  if (!runtime.shouldAcknowledgeAutoIssue(action, data)) {
    return;
  }

  try {
    await addIssueReaction(issue, options);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.warn(`Unable to add reaction to automatic issue-flow issue; continuing. ${detail}`);
  }
}

async function acknowledgeTriggerComment(issue, comment, options = {}) {
  try {
    await addTriggerCommentReaction(issue, comment, options);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.warn(`Unable to add reaction to trigger comment; continuing. ${detail}`);
  }
}

async function acknowledgeReviewComment(pr, comment, options = {}) {
  try {
    await addReviewCommentReaction(pr, comment, options);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    console.warn(`Unable to add reaction to review comment; continuing. ${detail}`);
  }
}

function runtimeCanRunAction(runtime, action) {
  return Array.isArray(runtime.SUPPORTED_ACTIONS) && runtime.SUPPORTED_ACTIONS.includes(action);
}

function resolveRepoDefaultAutomationLevel(options = {}) {
  return options.autoDefault || process.env.ISSUE_FLOW_AUTO_DEFAULT || 'off';
}

function resolveReviewEnabled(options = {}) {
  const raw = options.reviewEnabled !== undefined ? options.reviewEnabled : process.env.ISSUE_FLOW_REVIEW_ENABLED;
  const normalized = String(raw || '').trim().toLowerCase();
  return normalized === 'true' || normalized === '1';
}

function resolveAutomationDecision(issue, runtime, options = {}) {
  const decision = resolveCoreAutomationDecision(issue, {
    ...options,
    autoDefault: resolveRepoDefaultAutomationLevel(options),
  });
  if (decision.shouldRun && !runtimeCanRunAction(runtime, decision.action)) {
    return {
      ...decision,
      shouldRun: false,
      reason: 'unsupported_flow',
    };
  }
  return decision;
}

function resolveRuntimeResumeDecision(issue, runtime) {
  const decision = resolveResumeDecision(issue);
  if (decision.shouldRun && !runtimeCanRunAction(runtime, decision.action)) {
    return {
      ...decision,
      shouldRun: false,
      reason: 'unsupported_flow',
    };
  }
  return decision;
}

function pipelineFailureIssueNumber(result = {}) {
  return (
    result.issueNumber ||
    result.issue && (result.issue.issueNumber || result.issue.number)
  );
}

function pipelineFailureIssueUrl(result = {}) {
  return (
    result.issueUrl ||
    result.issue && (result.issue.issueUrl || result.issue.htmlUrl || result.issue.url)
  );
}

function pipelineFailureIssueLabels(result = {}) {
  const labels = result.labels || result.issue && result.issue.labels;
  if (Array.isArray(labels) && labels.length > 0) {
    return labels;
  }
  return [
    'type::ops',
    'status::active',
    'flow::build',
    'automation::build',
    'priority::p2',
    'size::M',
    'failure::ci',
    result.fingerprint && result.fingerprint.label,
  ].filter(Boolean);
}

function buildPipelineFailureIssueContext(result = {}, options = {}) {
  const number = pipelineFailureIssueNumber(result);
  if (!number) {
    return undefined;
  }
  const provider = resolveProvider(options);
  const repo = provider.resolveRepo({}, options);
  return {
    provider: provider.name,
    owner: repo.owner,
    repo: repo.repo,
    repoFullName: repo.fullName,
    projectId: repo.projectId,
    number,
    title: '',
    body: '',
    htmlUrl: pipelineFailureIssueUrl(result),
    state: 'open',
    labels: pipelineFailureIssueLabels(result),
  };
}

async function startAction(action, issue, options = {}, data = {}) {
  const runtime = loadRuntime(options);
  if (!runtimeCanRunAction(runtime, action)) {
    throw new Error(`Runtime does not support issue-flow action: ${action}`);
  }

  logIssueFlow('Starting action', {
    action,
    issue: `#${issue.number}`,
    dryRun: Boolean(options.dryRun),
  });
  const currentIssue = await fetchCurrentIssue(issue, options);
  const provider = providers[currentIssue.provider] || resolveProvider(options);
  const repo = {
    owner: currentIssue.owner,
    repo: currentIssue.repo,
    fullName: currentIssue.repoFullName,
    projectId: currentIssue.projectId,
  };
  const target = await resolveIssueTarget(currentIssue, provider, repo, options);
  const taskData = target.enabled ? { ...data, ...target } : data;
  const taskClaim = await claimActionTask(currentIssue, action, runtime, taskData, options);
  if (!taskClaim.claimed) {
    logIssueFlow('Skipping duplicate action task', {
      action,
      issue: `#${currentIssue.number}`,
      comment: taskClaim.comment && taskClaim.comment.id,
    });
    return {
      action,
      skipped: true,
      reason: 'duplicate_task',
      existingCommentId: taskClaim.comment && taskClaim.comment.id,
      existingCommentUrl: taskClaim.comment && (taskClaim.comment.html_url || taskClaim.comment.htmlUrl || taskClaim.comment.web_url),
    };
  }

  let result;
  try {
    result = runtime.run(action, currentIssue, options, taskData);
  } catch (error) {
    if (taskClaim.comment && taskClaim.comment.id) {
      try {
        await deleteIssueComment(currentIssue, taskClaim.comment.id, options);
      } catch (deleteError) {
        const detail = deleteError instanceof Error ? deleteError.message : String(deleteError);
        console.warn(`Unable to delete failed issue-flow task lock comment; continuing. ${detail}`);
      }
    }
    throw error;
  }

  await acknowledgeAutoIssue(currentIssue, action, runtime, taskData, options);
  if (taskClaim.comment && taskClaim.comment.id) {
    await updateIssueComment(currentIssue, taskClaim.comment.id, runtime.buildTaskComment(action, result, taskData), options);
  }
  logIssueFlow('Finished action', {
    action,
    issue: `#${currentIssue.number}`,
    runId: result.runId,
  });
  return {
    action,
    result,
  };
}

async function startPullRequestReview(pr, options = {}, data = {}) {
  const action = 'review';
  const runtime = loadRuntime(options);
  if (!runtimeCanRunAction(runtime, action)) {
    throw new Error(`Runtime does not support issue-flow action: ${action}`);
  }
  if (typeof runtime.resumeTask !== 'function') {
    throw new Error('Runtime does not support task resume');
  }

  logIssueFlow('Starting PR/MR review', {
    pr: `#${pr.number}`,
    dryRun: Boolean(options.dryRun),
  });
  const currentPr = data.currentPullRequest || await fetchCurrentPullRequest(pr, options);
  const taskData = {
    ...data,
    pullRequest: currentPr,
    sourceIssueNumber: options.issueNumber || runtime.extractSourceIssueNumberFromPullRequest(currentPr),
  };
  const existingReviewTask = findActionTaskComment(await listPullRequestComments(currentPr, options), action, runtime, taskData);
  if (existingReviewTask) {
    const reviewedHeadSha = runtime.extractReviewHeadShaFromTaskComment(existingReviewTask);
    if (reviewedHeadSha && reviewedHeadSha === currentPr.headSha) {
      logIssueFlow('Skipping duplicate PR/MR review task', {
        pr: `#${currentPr.number}`,
        head: currentPr.headSha,
        comment: existingReviewTask.id,
      });
      return {
        action,
        skipped: true,
        reason: 'duplicate_task',
        existingCommentId: existingReviewTask.id,
        existingCommentUrl: existingReviewTask.html_url || existingReviewTask.htmlUrl || existingReviewTask.web_url,
      };
    }

    const taskId = runtime.extractRunIdFromTaskComment(existingReviewTask);
    if (!taskId) {
      logIssueFlow('Skipping PR/MR review resume without task id', {
        pr: `#${currentPr.number}`,
        comment: existingReviewTask.id,
      });
      return {
        action,
        skipped: true,
        reason: 'review_task_pending',
        existingCommentId: existingReviewTask.id,
        existingCommentUrl: existingReviewTask.html_url || existingReviewTask.htmlUrl || existingReviewTask.web_url,
      };
    }

    const instruction = runtime.buildReviewResumeInstruction(currentPr, {
      previousHeadSha: reviewedHeadSha,
    });
    const resumeData = {
      ...taskData,
      agentrixTaskId: taskId,
    };
    const result = runtime.resumeTask(taskId, instruction, options, resumeData);
    if (existingReviewTask.id) {
      await updatePullRequestComment(
        currentPr,
        existingReviewTask.id,
        runtime.buildTaskComment(action, { ...result, runId: taskId }, resumeData),
        options
      );
    }
    logIssueFlow('Resumed PR/MR review task', {
      pr: `#${currentPr.number}`,
      runId: result.runId,
      head: currentPr.headSha,
    });
    return {
      action,
      resumed: true,
      result,
      existingCommentId: existingReviewTask.id,
      existingCommentUrl: existingReviewTask.html_url || existingReviewTask.htmlUrl || existingReviewTask.web_url,
    };
  }

  const taskClaim = await claimPullRequestActionTask(currentPr, action, runtime, taskData, options);
  if (!taskClaim.claimed) {
    logIssueFlow('Skipping duplicate PR/MR review task', {
      pr: `#${currentPr.number}`,
      comment: taskClaim.comment && taskClaim.comment.id,
    });
    return {
      action,
      skipped: true,
      reason: 'duplicate_task',
      existingCommentId: taskClaim.comment && taskClaim.comment.id,
      existingCommentUrl: taskClaim.comment && (taskClaim.comment.html_url || taskClaim.comment.htmlUrl || taskClaim.comment.web_url),
    };
  }

  let result;
  try {
    result = runtime.run(action, currentPr, options, taskData);
  } catch (error) {
    if (taskClaim.comment && taskClaim.comment.id) {
      try {
        await deletePullRequestComment(currentPr, taskClaim.comment.id, options);
      } catch (deleteError) {
        const detail = deleteError instanceof Error ? deleteError.message : String(deleteError);
        console.warn(`Unable to delete failed issue-flow PR/MR task lock comment; continuing. ${detail}`);
      }
    }
    throw error;
  }

  if (taskClaim.comment && taskClaim.comment.id) {
    await updatePullRequestComment(currentPr, taskClaim.comment.id, runtime.buildTaskComment(action, result, taskData), options);
  }
  logIssueFlow('Finished PR/MR review', {
    pr: `#${currentPr.number}`,
    runId: result.runId,
  });
  return {
    action,
    result,
  };
}

async function resumeTaskForReviewComment(target, taskId, instruction, options = {}, data = {}) {
  const action = 'task_resume';
  const runtime = loadRuntime(options);
  if (typeof runtime.resumeTask !== 'function') {
    throw new Error('Runtime does not support task resume');
  }

  const taskData = {
    ...data,
    ...(data.issueTask ? { issue: target } : { pullRequest: target }),
    agentrixTaskId: taskId,
  };
  let taskComment;
  if (data.issueTask) {
    taskComment = await createIssueComment(target, runtime.buildTaskComment(action, {
      status: 'queued',
      runId: taskId,
      detailUrl: data.issueTask.detailUrl,
    }, taskData), options);
  }

  let result;
  try {
    result = runtime.resumeTask(taskId, instruction, options, taskData);
  } catch (error) {
    if (taskComment && taskComment.id) {
      try {
        await deleteIssueComment(target, taskComment.id, options);
      } catch (deleteError) {
        const detail = deleteError instanceof Error ? deleteError.message : String(deleteError);
        console.warn(`Unable to delete failed issue-flow resume comment; continuing. ${detail}`);
      }
    }
    throw error;
  }

  if (data.issueTask && taskComment && taskComment.id) {
    const commentBody = runtime.buildTaskComment(action, {
      ...result,
      runId: taskId,
      detailUrl: result.detailUrl || data.issueTask.detailUrl,
    }, taskData);
    await updateIssueComment(target, taskComment.id, commentBody, options);
  }
  return {
    action,
    result,
  };
}

function getSingleFlowLabel(issue = {}) {
  const flowLabels = normalizeLabels(issue.labels).filter((label) => label.startsWith('flow::'));
  return flowLabels.length === 1 ? flowLabels[0] : '';
}

function getPullRequestTaskAction(pr = {}) {
  const labels = normalizeLabels(pr.labels);
  if (labels.includes('mr-by::plan')) {
    return 'plan';
  }
  if (labels.includes('mr-by::build')) {
    return 'build';
  }
  return 'task_resume';
}

function listResumableIssueTasks(comments, runtime) {
  if (typeof runtime.extractIssueTaskFromTaskComment !== 'function' || !Array.isArray(comments)) {
    return [];
  }
  return comments
    .map((comment) => runtime.extractIssueTaskFromTaskComment(comment))
    .filter(Boolean);
}

function shouldSkipPullRequestReview(pr) {
  if (!pr || !pr.number) {
    return 'not_pull_request';
  }
  if (pr.draft) {
    return 'draft_pull_request';
  }
  if (pr.merged) {
    return 'merged_pull_request';
  }
  if (pr.state && pr.state !== 'open' && pr.state !== 'opened') {
    return 'pull_request_not_open';
  }
  if (normalizeLabels(pr.labels).includes('review::off')) {
    return 'pull_request_review_disabled';
  }
  return '';
}

function shouldCheckForResumableVisualPlanTask(issue = {}) {
  const labels = normalizeLabels(issue.labels);
  return !labels.includes('type::optimization')
    && labels.includes('feature:visual-plan:on')
    && labels.includes('flow::plan');
}

async function runAuto(options = {}, provided = {}) {
  const runtime = loadRuntime(options);
  const payload = provided.payload || loadEvent(options);
  logIssueFlow('Handling automatic issue-flow event');
  if (isPullRequestIssue(payload, options)) {
    logIssueFlow('Issue event belongs to a pull request; auto ignored');
    return {
      action: 'ignored',
      reason: 'pull_request',
    };
  }
  if (!shouldRunAutoForEvent(payload)) {
    const rawLabel = payload.label || payload.object_attributes && payload.object_attributes.label;
    const changedLabel = normalizeLabels([rawLabel])[0] || '';
    if (changedLabel === 'status::done' || changedLabel === 'status::drop') {
      const terminalIssue = await fetchCurrentIssue(provided.issue || buildIssueContext(payload, options), options);
      const terminalProvider = providers[terminalIssue.provider] || resolveProvider(options, payload);
      const terminalRepo = terminalProvider.resolveRepo(payload, options);
      const optimizationCompletion = await completeOptimizationForChildIssue(terminalProvider, terminalRepo, terminalIssue.number, options);
      if (optimizationCompletion.completed) return { action: 'optimization_completed', optimizationCompletion };
    }
    logIssueFlow('Automatic issue-flow skipped for non-routing labeled event');
    return {
      action: 'skipped',
      reason: 'label_not_routing',
    };
  }

  let issue = provided.issue || buildIssueContext(payload, options);
  issue = await fetchCurrentIssue(issue, options);
  const issueLabels = normalizeLabels(issue.labels);
  if (issueLabels.includes('status::done') || issueLabels.includes('status::drop')) {
    const terminalProvider = providers[issue.provider] || resolveProvider(options, payload);
    const terminalRepo = terminalProvider.resolveRepo(payload, options);
    const optimizationCompletion = await completeOptimizationForChildIssue(terminalProvider, terminalRepo, issue.number, options);
    if (optimizationCompletion.completed) return { action: 'optimization_completed', optimizationCompletion };
  }
  if (shouldCheckForResumableVisualPlanTask(issue)) {
    const planTasks = listResumableIssueTasks(await listIssueComments(issue, options), runtime)
      .filter((task) => task.action === 'plan');
    if (planTasks.length === 1) {
      logIssueFlow('Automatic Plan start skipped because the Decision review resumes its original task', { issue: `#${issue.number}` });
      return {
        action: 'skipped',
        reason: 'decision_review_resumes_original_task',
        issueNumber: issue.number,
        taskId: planTasks[0].taskId,
      };
    }
  }
  const decision = resolveAutomationDecision(issue, runtime, options);
  if (!decision.shouldRun) {
    logIssueFlow('Automatic issue-flow skipped', {
      issue: `#${issue.number}`,
      reason: decision.reason,
      flow: decision.flowLabel,
      automation: decision.automationLabel,
      effective: decision.effectiveLevel,
    });
    return {
      ...decision,
      action: 'skipped',
      selectedAction: decision.action,
    };
  }

  logIssueFlow('Automatic issue-flow allowed', {
    issue: `#${issue.number}`,
    action: decision.action,
    flow: decision.flowLabel,
    automation: decision.automationLabel,
    default: decision.repoDefaultLevel,
    effective: decision.effectiveLevel,
  });

  return startAction(decision.action, issue, options, {
    ...provided,
    payload,
    issue,
    auto: true,
    automationDecision: decision,
  });
}

async function runResume(options = {}, provided = {}) {
  const runtime = loadRuntime(options);
  const payload = provided.payload || loadEvent(options);
  let issue = provided.issue || buildIssueContext(payload, options);
  logIssueFlow('Starting resume', { issue: `#${issue.number}` });
  issue = await fetchCurrentIssue(issue, options);

  const decision = resolveRuntimeResumeDecision(issue, runtime);
  if (!decision.shouldRun) {
    return {
      ...decision,
      action: 'skipped',
      selectedAction: decision.action,
    };
  }

  return startAction(decision.action, issue, options, {
    ...provided,
    payload,
    issue,
    flowLabel: decision.flowLabel,
  });
}

async function runComment(options = {}, provided = {}) {
  const runtime = loadRuntime(options);
  const payload = provided.payload || loadEvent(options);
  logIssueFlow('Handling issue comment event');
  if (isPullRequestIssue(payload, options)) {
    logIssueFlow('Issue comment belongs to a pull request; ignored');
    return {
      action: 'ignored',
      reason: 'pull_request',
    };
  }
  const comment = getCommentContext(payload, options);
  if (isBotComment(payload, options)) {
    logIssueFlow('Bot comment ignored');
    return {
      action: 'ignored',
      reason: 'bot_comment',
    };
  }

  const route = runtime.extractMention(comment.body, options);
  if (!route.triggered) {
    logIssueFlow('No runtime mention found; ignored');
    return {
      action: 'ignored',
      reason: 'no_mention',
    };
  }

  const issue = buildIssueContext(payload, options);
  await acknowledgeTriggerComment(issue, comment, options);

  if (route.instruction) {
    const currentIssue = await fetchCurrentIssue(issue, options);
    if (getSingleFlowLabel(currentIssue) === 'flow::clarify') {
      const issueTasks = listResumableIssueTasks(await listIssueComments(currentIssue, options), runtime);
      if (issueTasks.length === 1) {
        const instruction = typeof runtime.buildIssueCommentResumeInstruction === 'function'
          ? runtime.buildIssueCommentResumeInstruction(currentIssue, comment, {
            issueTask: issueTasks[0],
            instruction: route.instruction,
          })
          : route.instruction;
        const resume = await resumeTaskForReviewComment(currentIssue, issueTasks[0].taskId, instruction, options, {
          payload,
          issue: currentIssue,
          issueTask: issueTasks[0],
          comment,
          instruction: route.instruction,
        });
        logIssueFlow('Resumed issue task from clarify comment', {
          issue: `#${currentIssue.number}`,
          task: issueTasks[0].taskId,
          taskAction: issueTasks[0].action,
        });
        return {
          ...resume,
          issueNumber: currentIssue.number,
          taskId: issueTasks[0].taskId,
          taskAction: issueTasks[0].action,
        };
      }
      logIssueFlow('Clarify comment did not have exactly one resumable issue task', {
        issue: `#${currentIssue.number}`,
        tasks: issueTasks.length,
      });
    }

    return startAction('general', currentIssue, options, {
      payload,
      issue: currentIssue,
      comment,
      instruction: route.instruction,
    });
  }

  return runResume(options, {
    payload,
    issue,
    comment,
  });
}

async function runPrMerged(options = {}) {
  const transition = await prMerged.runPrMerged(options);
  if (transition.action !== 'applied' || !transition.sourceIssue) {
    return transition;
  }

  logIssueFlow('Auto-resuming after merged PR transition', {
    issue: `#${transition.sourceIssue.number}`,
    kind: transition.kind,
    flow: transition.flow,
    status: transition.status,
  });
  if (transition.kind === 'decision') {
    if (!transition.taskId || !transition.pullRequestNumber) {
      return {
        transition,
        autoResume: {
          action: 'skipped',
          reason: transition.taskId ? 'decision_merge_request_missing' : 'decision_task_missing',
        },
      };
    }
    const runtime = loadRuntime(options);
    const instruction = typeof runtime.buildDecisionMergeResumeInstruction === 'function'
      ? runtime.buildDecisionMergeResumeInstruction(transition)
      : 'Decision MR 已批准并合并，请继续当前任务并生成 Plan MR。';
    const pullRequest = {
      provider: transition.provider,
      owner: transition.sourceIssue.owner,
      repo: transition.sourceIssue.repo,
      repoFullName: transition.sourceIssue.repoFullName,
      projectId: transition.sourceIssue.projectId,
      number: transition.pullRequestNumber,
      htmlUrl: transition.pullRequestUrl,
      body: transition.pullRequestBody,
      state: 'closed',
      merged: true,
    };
    const autoResume = await resumeTaskForReviewComment(pullRequest, transition.taskId, instruction, options, {
      pullRequest,
      sourceIssueNumber: transition.issueNumber,
      taskAction: 'plan',
      decisionMerge: transition,
    });
    return { transition, autoResume };
  }
  const autoResume = await runAuto(options, {
    payload: {},
    issue: transition.sourceIssue,
    prMerged: transition,
  });
  return {
    transition,
    autoResume,
  };
}

async function runPipelineFailed(options = {}) {
  logIssueFlow('Handling pipeline failure intake');
  const result = await pipelineFailed.runFailureIntake(options);
  logIssueFlow('Pipeline failure intake finished', {
    action: result.action,
    reason: result.reason,
    issue: pipelineFailureIssueNumber(result) ? `#${pipelineFailureIssueNumber(result)}` : '',
  });
  const issue = buildPipelineFailureIssueContext(result, options);
  if (!issue || result.action === 'skipped' || result.dryRun) {
    return result;
  }

  logIssueFlow('Auto-resuming after pipeline failure intake', {
    issue: `#${issue.number}`,
    action: result.action,
  });
  const autoResume = await runAuto(options, {
    payload: {},
    issue,
    pipelineFailure: result,
  });
  return {
    failureIntake: result,
    autoResume,
  };
}

async function runReview(options = {}, provided = {}) {
  if (!resolveReviewEnabled(options)) {
    logIssueFlow('PR/MR review skipped', { reason: 'review_disabled' });
    return {
      action: 'skipped',
      reason: 'review_disabled',
    };
  }

  const payload = provided.payload || loadEvent(options);
  let pr;
  try {
    pr = provided.pullRequest || buildPullRequestContext(payload, options);
  } catch (error) {
    if (options.prNumber) {
      throw error;
    }
    logIssueFlow('PR/MR review skipped', { reason: 'not_pull_request' });
    return {
      action: 'skipped',
      reason: 'not_pull_request',
    };
  }

  const currentPr = await fetchCurrentPullRequest(pr, options);
  const skipReason = shouldSkipPullRequestReview(currentPr);
  if (skipReason) {
    logIssueFlow('PR/MR review skipped', {
      pr: currentPr.number ? `#${currentPr.number}` : '',
      reason: skipReason,
    });
    return {
      action: 'skipped',
      reason: skipReason,
      pullRequest: currentPr,
    };
  }

  return startPullRequestReview(currentPr, options, {
    ...provided,
    payload,
    pullRequest: currentPr,
    currentPullRequest: currentPr,
  });
}

async function runReviewComment(options = {}, provided = {}) {
  const runtime = loadRuntime(options);
  const payload = provided.payload || loadEvent(options);
  const event = isReviewCommentCreatedEvent(payload, options);
  if (!event.ok) {
    logIssueFlow('PR/MR review comment skipped', { reason: event.reason, action: event.eventAction || '' });
    return {
      action: 'skipped',
      reason: event.reason,
      eventAction: event.eventAction,
    };
  }

  let pr;
  try {
    pr = provided.pullRequest || buildPullRequestContext(payload, options);
  } catch (error) {
    logIssueFlow('PR/MR review comment skipped', { reason: 'not_pull_request_review_comment' });
    return {
      action: 'skipped',
      reason: 'not_pull_request_review_comment',
    };
  }

  if (!resolveReviewEnabled(options)) {
    logIssueFlow('PR/MR review comment skipped', { reason: 'review_disabled' });
    return {
      action: 'skipped',
      reason: 'review_disabled',
    };
  }

  const reviewComment = provided.reviewComment || getReviewCommentContext(payload, options);
  const source = parseSourceMarker(reviewComment.body);
  if (source.source_task_id || source.source_agent) {
    logIssueFlow('PR/MR review comment skipped', {
      reason: 'source_provenance',
      sourceTaskId: source.source_task_id || '',
      sourceAgent: source.source_agent || '',
      reviewComment: reviewComment.id,
    });
    return {
      action: 'skipped',
      reason: 'source_provenance',
      sourceTaskId: source.source_task_id || '',
      sourceAgent: source.source_agent || '',
      reviewComment: reviewComment.id,
    };
  }
  const currentPr = await fetchCurrentPullRequest(pr, options);
  const skipReason = shouldSkipPullRequestReview(currentPr);
  if (skipReason) {
    logIssueFlow('PR/MR review comment skipped', {
      pr: currentPr.number ? `#${currentPr.number}` : '',
      reason: skipReason,
    });
    return {
      action: 'skipped',
      reason: skipReason,
      pullRequest: currentPr,
      reviewComment: reviewComment.id,
    };
  }

  const taskId = runtime.extractAgentrixTaskIdFromPullRequest(currentPr);
  if (!taskId) {
    logIssueFlow('PR/MR review comment skipped', {
      pr: `#${currentPr.number}`,
      reason: 'missing_agentrix_task',
    });
    return {
      action: 'skipped',
      reason: 'missing_agentrix_task',
      pullRequest: currentPr.number,
      reviewComment: reviewComment.id,
    };
  }

  const sourceIssueNumber = options.issueNumber || runtime.extractSourceIssueNumberFromPullRequest(currentPr);
  const instruction = runtime.buildReviewCommentResumeInstruction(currentPr, reviewComment, {
    sourceIssueNumber,
  });
  await acknowledgeReviewComment(currentPr, reviewComment, options);
  const resume = await resumeTaskForReviewComment(currentPr, taskId, instruction, options, {
    ...provided,
    payload,
    pullRequest: currentPr,
    currentPullRequest: currentPr,
    reviewComment,
    sourceIssueNumber,
    taskAction: getPullRequestTaskAction(currentPr),
  });
  return {
    action: 'task_resume',
    result: resume.result,
    sourceIssue: sourceIssueNumber,
    pullRequest: currentPr.number,
    reviewComment: reviewComment.id,
    taskId,
  };
}

async function runDirectAction(action, options = {}, provided = {}) {
  const payload = provided.payload || loadEvent(options);
  const issue = provided.issue || buildIssueContext(payload, options);
  const instruction = action === 'general'
    ? provided.instruction || options.instruction || options._.join(' ').trim()
    : undefined;
  if (action === 'general' && !instruction) {
    throw new Error('general requires --instruction or positional instruction text');
  }
  return startAction(action, issue, options, {
    ...provided,
    payload,
    issue,
    instruction,
  });
}

async function main(argv = process.argv.slice(2)) {
  const { command, options } = parseArgs(argv);
  if (options.help || !command) {
    console.log(usage());
    return 0;
  }

  logIssueFlow('Command started', {
    command,
    runtime: options.runtime || process.env.ISSUE_FLOW_RUNTIME || DEFAULT_RUNTIME,
    dryRun: Boolean(options.dryRun),
  });
  switch (command) {
    case 'auto':
      await runAuto(options);
      break;
    case 'comment':
      await runComment(options);
      break;
    case 'review':
      await runReview(options);
      break;
    case 'review-comment':
      await runReviewComment(options);
      break;
    case 'pr-merged':
      await runPrMerged(options);
      break;
    case 'pipeline-failed':
      await runPipelineFailed(options);
      break;
    case 'resume':
      await runResume(options);
      break;
    case 'general':
    case 'triage':
    case 'plan':
    case 'build':
      await runDirectAction(command, options);
      break;
    default:
      throw new Error(`Unknown issue-flow dispatch command: ${command}`);
  }
  logIssueFlow('Command finished', { command });
  return 0;
}

module.exports = {
  buildIssueContext,
  buildPullRequestContext,
  findActionTaskComment,
  getReviewCommentContext,
  isReviewCommentCreatedEvent,
  listResumableIssueTasks,
  loadRuntime,
  main,
  parseArgs,
  resolveAutomationDecision,
  resolveRepoDefaultAutomationLevel,
  resolveReviewEnabled,
  resolveRuntimeResumeDecision,
  runAuto,
  runComment,
  runDirectAction,
  runPipelineFailed,
  runPrMerged,
  runReview,
  runReviewComment,
  runResume,
  resumeTaskForReviewComment,
  startAction,
  startPullRequestReview,
};

if (require.main === module) {
  main().catch((error) => {
    logIssueFlow('Command failed', {
      error: error instanceof Error ? error.message : String(error),
    });
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
