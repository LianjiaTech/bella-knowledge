#!/usr/bin/env node

const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const { resolveProvider, normalizeLabelName } = require('./providers.cjs');
const { labelDefinitionFor, labelGroupsForScope, resolveIssueSizeLabel } = require('./labels.cjs');
const { upsertSourceMarker } = require('./provenance.cjs');
const { resolveMilestoneSelection } = require('./milestones.cjs');

const MANAGED_LABELS = labelGroupsForScope('issue');
const LEGACY_AGENTRIX_TASK_MARKER_PATTERN = /<!--\s*issue-flow:agentrix:task=[^>]*-->\s*/i;

function usage() {
  return [
    'Usage: create-issue.cjs --title <title> --body-file <path> [options]',
    '',
    'Managed label options:',
    '  --type <type::feature|bug|debt|ops|docs|optimization>',
    '  --status <status::...>',
    '  --flow <flow::...>',
    '  --automation <automation::...>',
    '  --priority <priority::...>',
    '  --size <size::...>',
    '',
    'Other options:',
    '  --label <name>            Add a non-managed provider label. May be repeated.',
    '  --milestone <title|none>  Required when milestone target branches are enabled.',
    '  --agentrix-task-id <id>   Task id for the hidden source marker. Defaults from AGENTRIX_TASK_ID.',
    '  --provider <provider>     Git hosting provider: github or gitlab. Defaults from environment/repo.',
    '  --repo <owner/repo>       Repository/project override. Defaults to provider environment or git remote origin.',
    '  --dry-run',
    '  --help',
  ].join('\n');
}

function parseArgs(argv) {
  const options = {
    _: [],
    labels: [],
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

    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for ${arg}`);
    }
    if (arg === '--label') {
      options.labels.push(value);
      index += 1;
      continue;
    }

    const key = arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
    options[key] = value;
    index += 1;
  }

  return options;
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

function readBodyFile(bodyFile) {
  if (!bodyFile) {
    throw new Error('--body-file is required');
  }
  return fs.readFileSync(bodyFile, 'utf8').trim();
}

function validateTitle(title) {
  const normalized = String(title || '').trim();
  if (!normalized) {
    throw new Error('--title is required');
  }
  return normalized;
}

function managedGroupForLabel(labelName) {
  return Object.entries(MANAGED_LABELS).find(([, group]) => labelName.startsWith(group.prefix));
}

function collectManagedLabels(options) {
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

function collectCreateLabels(options) {
  const managedByKey = collectManagedLabels(options);
  const labels = Object.values(managedByKey);

  for (const raw of options.labels || []) {
    const label = normalizeLabelName(raw).trim();
    if (!label) {
      continue;
    }
    if (label.startsWith('mr-by::')) {
      throw new Error('mr-by::* labels are only valid for PR/MR objects');
    }
    const managed = managedGroupForLabel(label);
    if (managed) {
      const [key, config] = managed;
      if (!config.values.includes(label)) {
        throw new Error(`${key} must be one of: ${config.values.join(', ')}`);
      }
      throw new Error(`${label} is a managed label. Use --${key} ${label} instead of --label.`);
    }
    labels.push(label);
  }

  const seenPrefixes = new Map();
  for (const label of labels) {
    const managed = managedGroupForLabel(label);
    if (!managed) {
      continue;
    }
    const [key, config] = managed;
    const existing = seenPrefixes.get(config.prefix);
    if (existing && existing !== label) {
      throw new Error(`Only one ${config.prefix} label is allowed: ${existing}, ${label}`);
    }
    seenPrefixes.set(config.prefix, label);
    if (!MANAGED_LABELS[key].values.includes(label)) {
      throw new Error(`${key} must be one of: ${MANAGED_LABELS[key].values.join(', ')}`);
    }
  }

  return [...new Set(labels)];
}

function requiresSizeForCreate(options) {
  return options.flow === 'flow::plan' || options.flow === 'flow::build';
}

function validateCreateSizeGate(labels, options = {}) {
  if (!requiresSizeForCreate(options)) {
    return undefined;
  }
  const size = resolveIssueSizeLabel(labels);
  if (size.ok) {
    return size;
  }
  if (size.code === 'multiple_size_labels') {
    throw new Error(`Creating an issue with flow::plan/build requires exactly one size:: label; found: ${size.labels.join(', ')}.`);
  }
  if (size.code === 'invalid_size_label') {
    throw new Error(`Creating an issue with flow::plan/build requires a managed size label; found: ${size.labels.join(', ')}.`);
  }
  throw new Error(
    'Creating an issue with flow::plan/build requires --size size::<value>. Choose size::XS/S/M/L/XL; if unsure, use size::M and mention low confidence in the body or a follow-up comment.'
  );
}

function buildIssueBodyWithSourceMarker(body, taskId) {
  const content = String(body || '').replace(LEGACY_AGENTRIX_TASK_MARKER_PATTERN, '').trim();
  const sourceTaskId = String(taskId || process.env.AGENTRIX_TASK_ID || '').trim();
  return upsertSourceMarker(content, {
    sourceTaskId,
    sourceRuntime: sourceTaskId ? 'agentrix' : '',
  });
}

function managedDefinitionsForLabels(labels) {
  return labels
    .map((label) => labelDefinitionFor(label))
    .filter((definition) => definition && definition.scope === 'issue');
}

function bodySummary(body) {
  const normalized = String(body || '').replace(/\s+/g, ' ').trim();
  return normalized.length > 120 ? `${normalized.slice(0, 117)}...` : normalized;
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }

  const title = validateTitle(options.title);
  const labels = collectCreateLabels(options);
  validateCreateSizeGate(labels, options);
  const rawBody = readBodyFile(options.bodyFile);
  const body = buildIssueBodyWithSourceMarker(rawBody, options.agentrixTaskId || process.env.AGENTRIX_TASK_ID);
  const repoHint = resolveRepoHint(options);
  const provider = resolveProvider({ ...options, repo: repoHint }, {});
  const repo = provider.resolveRepo({}, { ...options, repo: repoHint });
  options.provider = provider.name;
  const selection = await resolveMilestoneSelection(options.milestone, provider, repo, options);

  if (options.dryRun) {
    console.log(
      JSON.stringify(
        {
          dryRun: true,
          provider: provider.name,
          repo: repo.fullName,
          title,
          bodySummary: bodySummary(body),
          labels,
          milestone: selection.milestone && selection.milestone.title || null,
        },
        null,
        2
      )
    );
    return 0;
  }

  const issue = await provider.createIssue({
    repo,
    title,
    body,
    labels,
    milestone: selection.milestone,
    managedLabelDefinitions: managedDefinitionsForLabels(labels),
    options,
  });

  console.log(
    JSON.stringify(
      {
        dryRun: false,
        provider: provider.name,
        repo: repo.fullName,
        issueNumber: issue.number,
        issueUrl: issue.htmlUrl,
        labels: issue.labels && issue.labels.length > 0 ? issue.labels : labels,
        milestone: issue.milestone && issue.milestone.title || null,
      },
      null,
      2
    )
  );
  return 0;
}

module.exports = {
  buildIssueBodyWithSourceMarker,
  collectCreateLabels,
  collectManagedLabels,
  main,
  parseArgs,
  requiresSizeForCreate,
  resolveRepoHint,
  validateCreateSizeGate,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
