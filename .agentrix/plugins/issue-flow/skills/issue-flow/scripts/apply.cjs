#!/usr/bin/env node

const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const { resolveProvider } = require('./providers.cjs');
const { labelGroupsForScope, requireSingleIssueSize } = require('./labels.cjs');
const { computeManagedLabelChanges } = require('../../../domain/index.cjs');
const {
  listMilestoneCandidates,
  milestoneTitle,
  resolveMilestoneConfig,
  resolveMilestoneSelection,
} = require('./milestones.cjs');

const MANAGED_LABELS = labelGroupsForScope('issue');

function usage() {
  return [
    'Usage: apply.cjs --issue-number <number> [options]',
    '',
    'Label options:',
    '  --type <type::feature|bug|debt|ops|docs|optimization>',
    '  --status <status::...>',
    '  --flow <flow::...>',
    '  --clear-flow         Remove any existing flow:: label without adding a new one.',
    '  --visual-plan-feature <feature:visual-plan:on>',
    '  --clear-visual-plan-feature  Remove the Visual Plan opt-in label.',
    '  --automation <automation::...>',
    '  --clear-automation   Remove any existing automation:: label without adding a new one.',
    '  --optimization-state <optimization::...>',
    '  --priority <priority::...>',
    '  --size <size::...>',
    '',
    'Issue body options:',
    '  --normalized-body <markdown>',
    '  --normalized-body-file <path>',
    '  --milestone <title|none>',
    '',
    'Provider options:',
    '  --provider <provider>   Git hosting provider: github or gitlab. Defaults from environment/repo.',
    '  --repo <owner/repo>     Repository/project override. Defaults to provider environment or git remote origin.',
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
    if (arg === '--help') {
      options.help = true;
      continue;
    }
    if (arg === '--dry-run') {
      options.dryRun = true;
      continue;
    }
    if (arg === '--clear-flow') {
      options.clearFlow = true;
      continue;
    }
    if (arg === '--clear-visual-plan-feature') {
      options.clearVisualPlanFeature = true;
      continue;
    }
    if (arg === '--clear-automation') {
      options.clearAutomation = true;
      continue;
    }
    if (!arg.startsWith('--')) {
      options._.push(arg);
      continue;
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

function getGitOriginUrl() {
  const result = spawnSync('git', ['config', '--get', 'remote.origin.url'], {
    encoding: 'utf8',
  });
  if (result.error) {
    return '';
  }
  if (result.status !== 0) {
    return '';
  }
  return result.stdout.trim();
}

function resolveRepoHint(options) {
  return options.repo || process.env.GITHUB_REPOSITORY || process.env.GITLAB_PROJECT_PATH || process.env.CI_PROJECT_PATH || getGitOriginUrl();
}

function parsePositiveInteger(value, name) {
  const parsed = Number.parseInt(String(value), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function normalizeLabelName(label) {
  if (typeof label === 'string') {
    return label;
  }
  if (label && typeof label.name === 'string') {
    return label.name;
  }
  return '';
}

function collectDesiredLabels(options) {
  const desired = {};
  for (const [key, config] of Object.entries(MANAGED_LABELS)) {
    const label = options[key];
    if (!label) {
      continue;
    }
    if (!config.values.includes(label)) {
      throw new Error(`${key} must be one of: ${config.values.join(', ')}`);
    }
    desired[key] = label;
  }
  return desired;
}

function collectClearKeys(options) {
  const clearKeys = [];
  if (options.clearFlow) {
    clearKeys.push('flow');
  }
  if (options.clearVisualPlanFeature) {
    clearKeys.push('visualPlanFeature');
  }
  if (options.clearAutomation) {
    clearKeys.push('automation');
  }
  return clearKeys;
}

function computeLabelChanges(currentLabels, desiredByKey, clearKeys = []) {
  return computeManagedLabelChanges(currentLabels, desiredByKey, clearKeys);
}

function computeNextLabels(currentLabels, labelsToAdd, labelsToRemove) {
  const remove = new Set(labelsToRemove);
  return [...new Set([...currentLabels.filter((label) => !remove.has(label)), ...labelsToAdd])];
}

function isPlanOrBuildFlow(desiredByKey) {
  return desiredByKey.flow === 'flow::plan' || desiredByKey.flow === 'flow::build';
}

function validateFlowSizeGate(currentLabels, desiredByKey, clearKeys = [], context = {}) {
  if (!isPlanOrBuildFlow(desiredByKey)) {
    return undefined;
  }
  const { labelsToAdd, labelsToRemove } = computeLabelChanges(currentLabels, desiredByKey, clearKeys);
  const nextLabels = computeNextLabels(currentLabels, labelsToAdd, labelsToRemove);
  return requireSingleIssueSize(nextLabels, context);
}

function hasBodySection(options) {
  return Boolean(options.normalizedBody || options.normalizedBodyFile);
}

function readNormalizedBody(options) {
  if (options.normalizedBodyFile) {
    return fs.readFileSync(options.normalizedBodyFile, 'utf8').trim();
  }
  if (options.normalizedBody) {
    return options.normalizedBody.trim();
  }
  return '';
}

function shouldSkipIssueBodyUpdate(desiredByKey) {
  return desiredByKey.flow === 'flow::clarify';
}

async function applyLabels(target, currentLabels, desiredByKey, clearKeys, options) {
  const { labelsToAdd, labelsToRemove } = computeLabelChanges(currentLabels, desiredByKey, clearKeys);

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, labelsToAdd, labelsToRemove }, null, 2));
    return;
  }

  const provider = resolveProvider(options, {});
  await provider.applyLabels(target, labelsToAdd, labelsToRemove, options);
}

async function applyIssueBody(target, issue, desiredByKey, options) {
  if (!hasBodySection(options)) {
    return;
  }

  if (shouldSkipIssueBodyUpdate(desiredByKey)) {
    if (options.dryRun) {
      console.log(
        JSON.stringify(
          {
            dryRun: true,
            bodySkipped: true,
            reason: 'flow::clarify does not edit issue body',
          },
          null,
          2
        )
      );
    }
    return;
  }

  const body = readNormalizedBody(options);

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, body }, null, 2));
    return;
  }

  const provider = resolveProvider(options, {});
  await provider.updateIssueBody(target, body, options);
}

async function applyIssueMilestone(target, provider, repo, options, issue = {}, desiredByKey = {}) {
  const config = resolveMilestoneConfig(options);
  if (options.milestone === undefined) {
    if (!config.enabled || milestoneTitle(issue) || !isPlanOrBuildFlow(desiredByKey)) {
      return { changed: false };
    }
    const candidates = await listMilestoneCandidates(provider, repo, options);
    if (candidates.length === 0) return { changed: false };
    const titles = candidates.map((candidate) => candidate.title).join(', ');
    throw new Error(
      `Milestone selection is required. Ask the user to choose one of: ${titles}, none; then pass --milestone <title|none>.`
    );
  }
  if (!config.enabled) {
    throw new Error('Enable milestone.enabled in .issue-flow/config.json before using --milestone.');
  }
  const selection = await resolveMilestoneSelection(options.milestone, provider, repo, options);
  await provider.setIssueMilestone(target, selection.milestone, options);
  return {
    changed: true,
    milestone: selection.milestone ? selection.milestone.title : null,
  };
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }
  const issueNumber = parsePositiveInteger(options.issueNumber || options.issue, '--issue-number');
  const repoHint = resolveRepoHint(options);
  const provider = resolveProvider({ ...options, repo: repoHint }, {});
  const repo = provider.resolveRepo({}, { ...options, repo: repoHint });
  options.provider = provider.name;
  const target = {
    ...repo,
    provider: provider.name,
    issueNumber,
    number: issueNumber,
  };
  const desiredByKey = collectDesiredLabels(options);
  const clearKeys = collectClearKeys(options);
  if (Object.keys(desiredByKey).length === 0 && clearKeys.length === 0 && !hasBodySection(options) && options.milestone === undefined) {
    throw new Error('Nothing to apply. Pass at least one managed label or triage body option.');
  }

  const issue = options.dryRun
    ? { body: '', labels: [] }
    : await provider.getIssueForApply(target, options);
  const currentLabels = Array.isArray(issue.labels) ? issue.labels.map(normalizeLabelName).filter(Boolean) : [];

  validateFlowSizeGate(currentLabels, desiredByKey, clearKeys, { issueNumber });
  await applyIssueMilestone(target, provider, repo, options, issue, desiredByKey);
  await applyLabels(target, currentLabels, desiredByKey, clearKeys, options);
  await applyIssueBody(target, issue, desiredByKey, options);
}

module.exports = {
  collectClearKeys,
  collectDesiredLabels,
  computeLabelChanges,
  computeNextLabels,
  applyIssueMilestone,
  isPlanOrBuildFlow,
  readNormalizedBody,
  shouldSkipIssueBodyUpdate,
  main,
  parseArgs,
  validateFlowSizeGate,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
