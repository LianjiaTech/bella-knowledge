#!/usr/bin/env node

const { spawnSync } = require('node:child_process');
const { resolveProvider } = require('./providers.cjs');

const DEFAULT_STATUS_LABEL = 'status::active';
const DEFAULT_FLOW_LABEL = 'flow::triage';
const STATUS_PREFIX = 'status::';
const FLOW_PREFIX = 'flow::';
const AUTOMATION_OFF_LABEL = 'automation::off';

function usage() {
  return [
    'Usage: intake.cjs --issue-number <number> [options]',
    '',
    'Adds default issue-flow labels when missing.',
    '',
    'Options:',
    '  --issue-number <num>  Source issue number.',
    '  --provider <name>     Git hosting provider: github or gitlab. Defaults from environment/repo.',
    '  --repo <owner/repo>   Repository/project override. Defaults from provider environment or git remote origin.',
    '  --dry-run             Print intended behavior without changing remote state.',
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
  if (label && typeof label.title === 'string') {
    return label.title;
  }
  return '';
}

function computeIssueIntakeLabels(currentLabels, options = {}) {
  const statusLabel = options.statusLabel || DEFAULT_STATUS_LABEL;
  const flowLabel = options.flowLabel || DEFAULT_FLOW_LABEL;
  const labels = currentLabels.map(normalizeLabelName).filter(Boolean);
  if (labels.includes(AUTOMATION_OFF_LABEL)) {
    return [];
  }
  const hasStatus = labels.some((label) => label.startsWith(STATUS_PREFIX));
  const hasFlow = labels.some((label) => label.startsWith(FLOW_PREFIX));
  const labelsToAdd = [];

  if (!hasStatus) {
    labelsToAdd.push(statusLabel);
  }
  if (!hasFlow) {
    labelsToAdd.push(flowLabel);
  }

  return labelsToAdd.filter((label, index) => labelsToAdd.indexOf(label) === index);
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

function resolveRepoHint(options, env = process.env, getOriginUrl = getGitOriginUrl) {
  return options.repo || env.GITHUB_REPOSITORY || env.GITLAB_PROJECT_PATH || env.CI_PROJECT_PATH || getOriginUrl();
}

async function runIntake(options) {
  const issueNumber = parsePositiveInteger(options.issueNumber || options.issue, '--issue-number');
  const repoHint = resolveRepoHint(options);
  const provider = resolveProvider({ ...options, repo: repoHint }, {});
  const repo = provider.resolveRepo({}, { ...options, repo: repoHint });
  const target = {
    ...repo,
    provider: provider.name,
    issueNumber,
    number: issueNumber,
  };
  const issue = options.dryRun
    ? { labels: [] }
    : await provider.getIssueForApply(target, options);
  const currentLabels = Array.isArray(issue.labels) ? issue.labels.map(normalizeLabelName).filter(Boolean) : [];
  const labelsToAdd = computeIssueIntakeLabels(currentLabels);
  const skipped = currentLabels.includes(AUTOMATION_OFF_LABEL)
    ? 'automation_off'
    : labelsToAdd.length === 0
      ? 'already_labeled'
      : undefined;

  if (options.dryRun) {
    console.log(JSON.stringify({ dryRun: true, labelsToAdd, skipped }, null, 2));
    return { added: labelsToAdd, skipped };
  }

  if (labelsToAdd.length > 0) {
    await provider.applyLabels(target, labelsToAdd, [], options);
  }

  console.log(JSON.stringify({ added: labelsToAdd, skipped }, null, 2));
  return { added: labelsToAdd, skipped };
}

async function applyIssueIntakeLabels({ core, github, context }) {
  const issue = context.payload.issue;
  if (!issue) {
    core.info('No issue payload found; skipping intake labels.');
    return { added: [] };
  }

  const currentLabelsResponse = await github.rest.issues.listLabelsOnIssue({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: issue.number,
    per_page: 100,
  });
  const currentLabels = currentLabelsResponse.data.map(normalizeLabelName).filter(Boolean);
  const labelsToAdd = computeIssueIntakeLabels(currentLabels);
  const skipped = currentLabels.includes(AUTOMATION_OFF_LABEL)
    ? 'automation_off'
    : labelsToAdd.length === 0
      ? 'already_labeled'
      : undefined;

  core.info(`Current labels: ${currentLabels.join(', ') || '(none)'}`);
  core.info(`Intake labels to add: ${labelsToAdd.join(', ') || '(none)'}`);

  if (labelsToAdd.length > 0) {
    await github.rest.issues.addLabels({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: issue.number,
      labels: labelsToAdd,
    });
  }

  return { added: labelsToAdd, skipped };
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }
  await runIntake(options);
  return 0;
}

module.exports = applyIssueIntakeLabels;
module.exports.computeIssueIntakeLabels = computeIssueIntakeLabels;
module.exports.main = main;
module.exports.normalizeLabelName = normalizeLabelName;
module.exports.parseArgs = parseArgs;
module.exports.resolveRepoHint = resolveRepoHint;
module.exports.runIntake = runIntake;

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
