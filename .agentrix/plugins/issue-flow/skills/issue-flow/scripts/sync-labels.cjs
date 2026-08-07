#!/usr/bin/env node

const { spawnSync } = require('node:child_process');
const { labelsForScope } = require('./labels.cjs');
const { planLabelSync, providerLabelDefinition, resolveProvider } = require('./providers.cjs');

function usage() {
  return [
    'Usage: sync-labels.cjs [options]',
    '',
    'Synchronizes issue-flow managed labels with the Git provider.',
    '',
    'Options:',
    '  --provider <provider>   Git hosting provider: github or gitlab. Defaults from environment/repo.',
    '  --repo <owner/repo>     Repository/project override. Defaults to provider environment or git remote origin.',
    '  --dry-run               Print the labels that would be ensured without provider reads or writes.',
    '  --check                 Read provider labels and fail if any label is missing or drifted.',
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
    if (arg === '--check') {
      options.check = true;
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

  if (options.dryRun && options.check) {
    throw new Error('--dry-run and --check are mutually exclusive');
  }

  return options;
}

function runOutput(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    cwd: options.cwd,
  });
  if (result.error || result.status !== 0) {
    return '';
  }
  return result.stdout.trim();
}

function getGitOriginUrl() {
  return runOutput('git', ['config', '--get', 'remote.origin.url']);
}

function resolveRepoHint(options) {
  return options.repo || process.env.GITHUB_REPOSITORY || process.env.GITLAB_PROJECT_PATH || process.env.CI_PROJECT_PATH || getGitOriginUrl();
}

function countResults(results) {
  const summary = {
    created: 0,
    updated: 0,
    skipped: 0,
    failed: 0,
    missing: 0,
    drifted: 0,
    planned: 0,
  };
  for (const result of results) {
    if (result.action === 'created' || result.action === 'create') {
      summary.created += 1;
    } else if (result.action === 'updated' || result.action === 'update') {
      summary.updated += 1;
    } else if (result.action === 'skipped' || result.action === 'skip') {
      summary.skipped += 1;
    } else if (result.action === 'missing') {
      summary.missing += 1;
    } else if (result.action === 'drifted') {
      summary.drifted += 1;
    } else if (result.action === 'ensure') {
      summary.planned += 1;
    }
    if (result.error) {
      summary.failed += 1;
    }
  }
  return summary;
}

async function checkLabels(provider, repo, definitions, options) {
  const results = [];
  for (const definition of definitions) {
    try {
      const existing = await provider.getLabel(repo, definition.name, options);
      const action = planLabelSync(provider.name, existing, definition);
      results.push({
        name: definition.name,
        action: action === 'create' ? 'missing' : action === 'update' ? 'drifted' : 'skipped',
        expected: providerLabelDefinition(provider.name, definition),
      });
    } catch (error) {
      results.push({
        name: definition.name,
        action: 'failed',
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
  return results;
}

async function syncLabels(provider, repo, definitions, options) {
  const results = [];
  for (const definition of definitions) {
    try {
      const result = options.dryRun
        ? {
            name: definition.name,
            action: 'ensure',
            expected: providerLabelDefinition(provider.name, definition),
          }
        : await provider.ensureLabelDefinition(repo, definition, options);
      results.push(result);
    } catch (error) {
      results.push({
        name: definition.name,
        action: 'failed',
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
  return results;
}

function outputResult(provider, repo, results, options) {
  const summary = countResults(results);
  const payload = {
    provider: provider.name,
    repo: repo.fullName,
    mode: options.check ? 'check' : options.dryRun ? 'dry-run' : 'sync',
    summary,
    labels: results,
  };
  console.log(JSON.stringify(payload, null, 2));
  return payload;
}

async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) {
    console.log(usage());
    return 0;
  }

  const provider = resolveProvider({ ...options, repo: resolveRepoHint(options) }, {});
  const repo = provider.resolveRepo({}, { ...options, repo: resolveRepoHint(options) });
  const definitions = labelsForScope('all');
  const results = options.check
    ? await checkLabels(provider, repo, definitions, options)
    : await syncLabels(provider, repo, definitions, options);
  const payload = outputResult(provider, repo, results, options);

  if (payload.summary.failed > 0 || payload.summary.missing > 0 || payload.summary.drifted > 0) {
    return 1;
  }
  return 0;
}

module.exports = {
  checkLabels,
  countResults,
  main,
  parseArgs,
  syncLabels,
};

if (require.main === module) {
  main().then(
    (code) => {
      process.exitCode = code;
    },
    (error) => {
      console.error(error instanceof Error ? error.message : String(error));
      process.exitCode = 1;
    }
  );
}
