#!/usr/bin/env node

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const DEFAULT_CONFIG_PATH = '.issue-flow/config.json';
const PHASES = new Set(['triage', 'plan', 'build', 'review']);

function usage() {
  return [
    'Usage: node task-context.cjs --issue <num> [options]',
    '',
    'Options:',
    '  --output <path>       Write the context index to this path. Phase files are written beside it.',
    '  --config <path>       Issue Flow config path. Defaults to .issue-flow/config.json.',
    '  --base-url <url>      Issue Flow service URL.',
  ].join('\n');
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--help' || arg === '-h') {
      options.help = true;
      continue;
    }
    if (!arg.startsWith('--')) throw new Error(`Unexpected argument: ${arg}`);
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) throw new Error(`Missing value for ${arg}`);
    const key = arg.slice(2).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
    options[key] = value;
    index += 1;
  }
  return options;
}

function readConfig(configPath) {
  const absolutePath = path.resolve(process.cwd(), configPath || DEFAULT_CONFIG_PATH);
  if (!fs.existsSync(absolutePath)) throw new Error(`Issue Flow config not found: ${absolutePath}`);
  return JSON.parse(fs.readFileSync(absolutePath, 'utf8'));
}

function resolveRoute(options, config) {
  const baseUrl = String(options.baseUrl || process.env.ISSUE_FLOW_BASE_URL || config.baseUrl || '')
    .trim()
    .replace(/\/+$/, '');
  const gitServerId = String(process.env.ISSUE_FLOW_GIT_SERVER_ID || config.gitServerId || '').trim();
  const projectId = String(process.env.ISSUE_FLOW_PROJECT_ID || process.env.CI_PROJECT_ID || config.projectId || '').trim();
  const issueNumber = Number(options.issue || 0);
  if (!baseUrl) throw new Error('Issue Flow base URL is required. Use --base-url, ISSUE_FLOW_BASE_URL, or baseUrl in .issue-flow/config.json.');
  if (!gitServerId) throw new Error('Issue Flow Git server ID is required. Set ISSUE_FLOW_GIT_SERVER_ID or configure gitServerId in .issue-flow/config.json.');
  if (!projectId) throw new Error('Provider project ID is required. Set ISSUE_FLOW_PROJECT_ID or configure projectId in .issue-flow/config.json.');
  if (!Number.isInteger(issueNumber) || issueNumber <= 0) throw new Error('--issue must be a positive integer.');
  return { baseUrl, gitServerId, projectId, issueNumber };
}

function defaultOutputPath(route) {
  return path.join(os.tmpdir(), 'issue-flow', 'task-context', `${route.issueNumber}-${process.pid}`, 'index.json');
}

async function fetchTaskContext(route) {
  const url = new URL(
    `/api/repositories/${encodeURIComponent(route.gitServerId)}/${encodeURIComponent(route.projectId)}/issues/${route.issueNumber}/task-context`,
    `${route.baseUrl}/`,
  );
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = body && (body.message || body.error) || `HTTP ${response.status}`;
    throw new Error(`Issue Flow task context request failed: ${detail}`);
  }
  return body;
}

function writeTaskContext(context, outputPath) {
  const absoluteOutputPath = path.resolve(outputPath);
  const outputDirectory = path.dirname(absoluteOutputPath);
  const phases = Array.isArray(context.phases) ? context.phases : [];
  fs.mkdirSync(outputDirectory, { recursive: true });

  const phaseFiles = {};
  const manifestPhases = [];
  for (const phase of PHASES) {
    const phaseContext = phases.find((item) => item && item.phase === phase) || { phase, tasks: [] };
    const tasks = Array.isArray(phaseContext.tasks) ? phaseContext.tasks : [];
    const phasePath = path.join(outputDirectory, `${phase}.json`);
    fs.writeFileSync(phasePath, `${JSON.stringify({
      repositoryId: context.repositoryId,
      issueNumber: context.issueNumber,
      phase,
      tasks,
    }, null, 2)}\n`, 'utf8');
    const eventCount = tasks.reduce((total, task) => total + (Array.isArray(task.events) ? task.events.length : 0), 0);
    phaseFiles[phase] = phasePath;
    manifestPhases.push({ phase, path: phasePath, taskCount: tasks.length, eventCount });
  }

  const manifest = {
    repositoryId: context.repositoryId,
    issueNumber: context.issueNumber,
    phases: manifestPhases,
  };
  fs.writeFileSync(absoluteOutputPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  return { path: absoluteOutputPath, phaseFiles, phases: manifestPhases };
}

async function run(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  if (options.help) return { help: usage() };
  const config = readConfig(options.config);
  const route = resolveRoute(options, config);
  const context = await fetchTaskContext(route);
  const written = writeTaskContext(context, options.output || defaultOutputPath(route));
  return {
    path: written.path,
    issueNumber: route.issueNumber,
    phaseFiles: written.phaseFiles,
    taskCount: written.phases.reduce((total, phase) => total + phase.taskCount, 0),
    eventCount: written.phases.reduce((total, phase) => total + phase.eventCount, 0),
  };
}

async function main(argv = process.argv.slice(2)) {
  const result = await run(argv);
  if (result.help) {
    process.stdout.write(`${result.help}\n`);
    return;
  }
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

module.exports = {
  fetchTaskContext,
  parseArgs,
  readConfig,
  resolveRoute,
  run,
  writeTaskContext,
};

if (require.main === module) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
