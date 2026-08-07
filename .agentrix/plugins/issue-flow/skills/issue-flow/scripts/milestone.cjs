#!/usr/bin/env node

const { loadEventPayload } = require('./events.cjs');
const { resolveProvider } = require('./providers.cjs');
const {
  listMilestoneCandidates,
  matchesConfiguredBranch,
  resolveMilestoneConfig,
} = require('./milestones.cjs');

function usage() {
  return [
    'Usage: milestone.cjs <list|sync> [options]',
    '',
    'Commands:',
    '  list   List open milestones backed by valid target branches.',
    '  sync   Create/reopen or close a milestone from a branch event.',
    '',
    'Options:',
    '  --event <path>',
    '  --provider <github|gitlab>',
    '  --repo <owner/repo>',
    '  --config <path>',
    '  --dry-run',
  ].join('\n');
}

function parseArgs(argv) {
  const options = { _: [] };
  const command = argv[0];
  for (let index = 1; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--help') { options.help = true; continue; }
    if (arg === '--dry-run') { options.dryRun = true; continue; }
    if (!arg.startsWith('--')) { options._.push(arg); continue; }
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) throw new Error(`Missing value for ${arg}`);
    options[arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase())] = value;
    index += 1;
  }
  return { command, options };
}

function stripHeadsRef(value) {
  return String(value || '').replace(/^refs\/heads\//, '').trim();
}

function extractBranchEvent(payload = {}, env = process.env) {
  const refType = payload.ref_type || payload.refType || env.GITLAB_BRIDGE_REF_TYPE;
  const branch = stripHeadsRef(payload.ref || payload.ref_name || payload.refName || env.GITLAB_BRIDGE_REF_NAME);
  const eventName = String(payload.event_name || env.GITHUB_EVENT_NAME || env.GITLAB_BRIDGE_EVENT_NAME || '').toLowerCase();
  if (refType && refType !== 'branch') return { action: 'ignored', branch: '' };
  if (eventName === 'create' || payload.action === 'create' || payload.action === 'created') return { action: 'create', branch };
  if (eventName === 'delete' || payload.action === 'delete' || payload.action === 'deleted') return { action: 'delete', branch };
  return { action: 'ignored', branch };
}

async function main(argv = process.argv.slice(2)) {
  const { command, options } = parseArgs(argv);
  if (options.help || !command) { console.log(usage()); return 0; }
  const loaded = loadEventPayload(options);
  const payload = loaded.payload || {};
  const provider = resolveProvider(options, payload);
  const repo = provider.resolveRepo(payload, options);
  const config = resolveMilestoneConfig(options);

  if (command === 'list') {
    const milestones = await listMilestoneCandidates(provider, repo, options);
    console.log(JSON.stringify({ enabled: config.enabled, provider: provider.name, repo: repo.fullName, milestones }, null, 2));
    return 0;
  }
  if (command !== 'sync') throw new Error(`Unknown milestone command: ${command}`);
  if (!config.enabled) {
    console.log(JSON.stringify({ enabled: false, action: 'ignored', reason: 'feature_disabled' }, null, 2));
    return 0;
  }

  const event = extractBranchEvent(payload);
  if (!event.branch || event.action === 'ignored') {
    console.log(JSON.stringify({ enabled: true, action: 'ignored', reason: 'not_branch_lifecycle_event' }, null, 2));
    return 0;
  }
  if (!matchesConfiguredBranch(event.branch, config)) {
    console.log(JSON.stringify({ enabled: true, action: 'ignored', reason: 'branch_pattern_mismatch', branch: event.branch }, null, 2));
    return 0;
  }
  const result = event.action === 'create'
    ? await provider.ensureMilestone(repo, event.branch, options)
    : await provider.closeMilestone(repo, event.branch, options);
  console.log(JSON.stringify({ enabled: true, provider: provider.name, repo: repo.fullName, branch: event.branch, result }, null, 2));
  return 0;
}

module.exports = { extractBranchEvent, main, parseArgs, stripHeadsRef };

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
