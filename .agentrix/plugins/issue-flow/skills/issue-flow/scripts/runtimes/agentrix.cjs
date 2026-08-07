/**
 * [INPUT]: 依赖 provenance.cjs 的 source marker 能力、项目级 instructions 与 Node.js 子进程环境
 * [OUTPUT]: 对外提供按 issue 语义选择且含项目说明的 Agentrix prompt、agentrix-run package、run/resume args、环境清洗与 task comment 构造执行函数
 * [POS]: scripts/runtimes 的 Agentrix adapter，把 issue/PR 事件转换为 agentrix-run 可消费的确定性调用
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { sourceIssueNumber } = require('../../../../domain/index.cjs');
const { spawnSync } = require('node:child_process');
const { buildSourceMarker, parseSourceMarker } = require('../provenance.cjs');

const DEFAULT_AGENT = 'codex';
const DEFAULT_RESPONSE_MODE = 'async';
const DEFAULT_AGENTRIX_RUN_VERSION = 'latest';
const DEFAULT_MENTION = '@agentrix';
const MENTION_PATTERN = /(^|[^A-Za-z0-9._-])(?:@agentrix|\/agentrix)(?=$|[^A-Za-z0-9._-])/i;
const MENTION_REPLACE_PATTERN = /(^|[^A-Za-z0-9._-])(?:@agentrix|\/agentrix)(?=$|[^A-Za-z0-9._-])/gi;
const DEFAULT_PROJECT_CONFIG_PATH = '.issue-flow/config.json';
const DEFAULT_PROJECT_INSTRUCTIONS_PATH = '.issue-flow/instructions.md';
const DEFAULT_PROMPTS_DIR = '.issue-flow/prompts';
const DEFAULT_TEMPLATES_DIR = '.issue-flow/templates';
const DEFAULT_PLAN_ROOT_DIR = '.issue-flow/issues';
const TASK_COMMENT_RUN_PATTERN = /^(?:[-*]\s+)?Run:\s*`([^`]+)`\s*$/im;
const TASK_COMMENT_DETAIL_URL_PATTERN = /Agentrix task queued:\s*\[open task\]\(([^)]+)\)/i;
const REVIEW_COMMENT_TASK_PATTERN = /^(?:[-*]\s+)?Review task:\s*`([^`]+)`\s*$/im;
const REVIEW_COMMENT_HEAD_PATTERN = /^(?:[-*]\s+)?Head:\s*`([^`]+)`\s*$/im;
const ISSUE_TASK_COMMENT_MARKER_PATTERN = /<!--\s*issue-flow:agentrix:task:(triage|plan|build)\s*-->/i;
const PLAN_SUBDIR = 'plan';
const FEATURE_PLAN_FILE = '001-implementation.md';
const BUG_PLAN_FILE = '001-root-cause-and-fix.md';
const PLAN_ENTRY_FILE = 'data/plan.json.isv';
const PLAN_DATA_FILE = 'data/plan.json.isv';
const OPTIMIZATION_DATA_FILE = 'data/optimization-data.json';
const PLAN_BRIEF_FILE = 'visual-brief.md';
const VISUAL_BRIEF_TEMP_ROOT = path.join(os.tmpdir(), 'issue-flow', 'visual-plan');
const DECISION_ENTRY_FILE = 'decision/data/decision.json.isv';
const VISUAL_PLAN_FEATURE_PREFIX = 'feature:visual-plan:';
const VISUAL_PLAN_FEATURE_ON = 'feature:visual-plan:on';
const PROMPT_CONTEXT_LABEL_SKIP_PREFIXES = ['status::', 'flow::', 'automation::', VISUAL_PLAN_FEATURE_PREFIX];
const PIPELINE_FAILURE_MARKER_PATTERN = /<!--\s*issue-flow:pipeline-failure\b/i;
const PROVIDER_TOKEN_ENV_KEYS = [
  'GITHUB_TOKEN',
  'GH_TOKEN',
  'ISSUE_FLOW_GITLAB_TOKEN',
  'GITLAB_TOKEN',
  'GL_TOKEN',
  'GITLAB_PRIVATE_TOKEN',
  'CI_JOB_TOKEN',
  'ISSUE_FLOW_GIT_TOKEN',
];
const GITHUB_TRIGGER_ENV_KEYS = [
  'GITHUB_EVENT_NAME',
  'GITHUB_EVENT_PATH',
  'GITHUB_REF',
  'GITHUB_REF_NAME',
  'GITHUB_SHA',
  'GITHUB_BASE_REF',
  'GITHUB_HEAD_REF',
];
const GITLAB_TRIGGER_ENV_KEYS = [
  'CI_COMMIT_REF_NAME',
  'CI_COMMIT_SHA',
  'CI_PIPELINE_SOURCE',
];

const PROMPT_FILES = {
  triage: 'triage.prompt.md',
  general: 'general.prompt.md',
  build: 'build.prompt.md',
  buildDocs: 'build-docs.prompt.md',
  buildCiFailure: 'build-ci-failure.prompt.md',
  review: 'review.prompt.md',
  planBug: 'plan-bug.prompt.md',
  planImpl: 'plan-impl.prompt.md',
  planVisualBug: 'plan-visual-bug.prompt.md',
  planVisualImpl: 'plan-visual-impl.prompt.md',
  planOptimization: 'plan-optimization.prompt.md',
};

const TEMPLATE_FILES = {
  planBug: 'plan-bug.md',
  planImpl: 'plan-impl.md',
};

const SUPPORTED_ACTIONS = ['triage', 'plan', 'build', 'general', 'review'];
const ACTION_CONTEXT_POLICIES = {
  triage: { input: 'issue' },
  plan: { input: 'issue', repository: true, output: 'plan' },
  build: { input: 'plan-or-issue', repository: true },
  general: { input: 'issue' },
};
const CLIENT_TASK_ACTIONS = {
  triage: 'triage',
  plan: 'plan',
  build: 'build',
  general: 'general',
  review: 'review',
};
const CLIENT_TASK_PROVIDERS = {
  github: 'gh',
  gitlab: 'gl',
};

function skillRootDir() {
  return path.resolve(__dirname, '..', '..');
}

function agentrixAssetsDir() {
  return path.join(skillRootDir(), 'assets', 'agentrix', 'runtime');
}

function readJsonFile(filePath, required = false) {
  if (!fs.existsSync(filePath)) {
    if (required) {
      throw new Error(`Config file not found: ${filePath}`);
    }
    return {};
  }
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function normalizeConfigShape(config) {
  return config && typeof config.agentrix === 'object' && !Array.isArray(config.agentrix)
    ? config.agentrix
    : config || {};
}

function resolveProjectConfigPath(options = {}) {
  return options.config || process.env.ISSUE_FLOW_CONFIG || DEFAULT_PROJECT_CONFIG_PATH;
}

function resolveRepoPath(value) {
  return path.isAbsolute(value) ? value : path.resolve(process.cwd(), value);
}

function resolveAgentrixConfig(options = {}) {
  const explicitConfigPath = Boolean(options.config || process.env.ISSUE_FLOW_CONFIG);
  const projectConfigPath = resolveRepoPath(resolveProjectConfigPath(options));
  const projectConfig = normalizeConfigShape(readJsonFile(projectConfigPath, explicitConfigPath));

  const promptsDir = options.promptsDir || projectConfig.promptsDir || DEFAULT_PROMPTS_DIR;
  const templatesDir = options.templatesDir || projectConfig.templatesDir || DEFAULT_TEMPLATES_DIR;
  const planRootDir = options.planRootDir || projectConfig.planRootDir || DEFAULT_PLAN_ROOT_DIR;

  return {
    mention: DEFAULT_MENTION,
    projectConfigPath,
    projectInstructionsPath: resolveRepoPath(options.projectInstructionsPath || DEFAULT_PROJECT_INSTRUCTIONS_PATH),
    projectPromptsDir: resolveRepoPath(promptsDir),
    projectTemplatesDir: resolveRepoPath(templatesDir),
    planRootDir: resolveRepoPath(planRootDir),
    planRootDirDisplay: normalizeRepoPath(planRootDir),
    defaultPromptsDir: path.join(agentrixAssetsDir(), 'prompts'),
    defaultTemplatesDir: path.join(agentrixAssetsDir(), 'templates'),
  };
}

function normalizeRepoPath(filePath) {
  const relative = path.isAbsolute(filePath) ? path.relative(process.cwd(), filePath) : filePath;
  return relative.replace(/\\/g, '/').replace(/^\.?\//, '');
}

function readFirstExisting(paths, label) {
  for (const filePath of paths) {
    if (fs.existsSync(filePath)) {
      return {
        path: filePath,
        body: fs.readFileSync(filePath, 'utf8').trim(),
      };
    }
  }
  throw new Error(`Missing ${label}. Looked in: ${paths.join(', ')}`);
}

function isPipelineFailureIssue(issue) {
  return hasLabel(issue, 'failure::ci') || PIPELINE_FAILURE_MARKER_PATTERN.test(String(issue && issue.body || ''));
}

function promptNameForAction(action, issue) {
  if (action === 'review') {
    return 'review';
  }
  if (action === 'build' && isPipelineFailureIssue(issue)) {
    return 'buildCiFailure';
  }
  if (action === 'build' && hasLabel(issue, 'type::docs')) {
    return 'buildDocs';
  }
  if (action !== 'plan') {
    return action;
  }
  if (hasLabel(issue, 'type::optimization')) {
    return 'planOptimization';
  }
  if (isVisualPlanEnabled(issue)) {
    return hasLabel(issue, 'type::bug') ? 'planVisualBug' : 'planVisualImpl';
  }
  return hasLabel(issue, 'type::bug') ? 'planBug' : 'planImpl';
}

function templateNameForIssue(issue) {
  return hasLabel(issue, 'type::bug') ? 'planBug' : 'planImpl';
}

function visualPlanFeatureMode(issue) {
  if (hasLabel(issue, 'type::optimization')) return 'on';
  return hasLabel(issue, VISUAL_PLAN_FEATURE_ON) ? 'on' : 'off';
}

function isVisualPlanEnabled(issue) {
  return visualPlanFeatureMode(issue) === 'on';
}

function planFileNameForIssue(issue) {
  return hasLabel(issue, 'type::bug') ? BUG_PLAN_FILE : FEATURE_PLAN_FILE;
}

function readPrompt(action, issue, options = {}) {
  const config = resolveAgentrixConfig(options);
  const promptName = promptNameForAction(action, issue);
  const fileName = PROMPT_FILES[promptName];
  const prompt = readFirstExisting(
    [path.join(config.projectPromptsDir, fileName), path.join(config.defaultPromptsDir, fileName)],
    `${promptName} prompt`
  );
  return { ...prompt, name: promptName };
}

function resolvePlanTemplate(issue, options = {}) {
  const config = resolveAgentrixConfig(options);
  const templateName = templateNameForIssue(issue);
  const fileName = TEMPLATE_FILES[templateName];
  return readFirstExisting(
    [path.join(config.projectTemplatesDir, fileName), path.join(config.defaultTemplatesDir, fileName)],
    `${templateName} template`
  );
}

function hasLabel(issue, label) {
  return Array.isArray(issue.labels) && issue.labels.includes(label);
}

function slugifyIssueTitle(title) {
  const slug = String(title || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'issue';
}

function issueDirectoryName(issue) {
  return `${issue.number}-${slugifyIssueTitle(issue.title)}`;
}

function buildIssuePlanDir(issue, options = {}) {
  const config = resolveAgentrixConfig(options);
  return path.join(config.planRootDir, issueDirectoryName(issue), PLAN_SUBDIR);
}

function buildIssueArtifactDir(issue, options = {}) {
  const config = resolveAgentrixConfig(options);
  return path.join(config.planRootDir, issueDirectoryName(issue));
}

function buildIssuePlanFile(issue, options = {}) {
  if (hasLabel(issue, 'type::optimization')) {
    return path.join(buildIssuePlanDir(issue, options), OPTIMIZATION_DATA_FILE);
  }
  return path.join(buildIssuePlanDir(issue, options), isVisualPlanEnabled(issue) ? PLAN_ENTRY_FILE : planFileNameForIssue(issue));
}

function buildIssueDecisionFile(issue, options = {}) {
  return path.join(buildIssueArtifactDir(issue, options), DECISION_ENTRY_FILE);
}

function buildIssuePlanDataFile(issue, options = {}) {
  return path.join(buildIssuePlanDir(issue, options), PLAN_DATA_FILE);
}

function buildIssuePlanBriefFile(issue) {
  return path.join(VISUAL_BRIEF_TEMP_ROOT, issueDirectoryName(issue), PLAN_BRIEF_FILE);
}

function issueActionBranch(issue, action) {
  return `${issueDirectoryName(issue)}/${action}`;
}

function listPlanInputFiles(issue, options = {}) {
  const config = resolveAgentrixConfig(options);
  if (!fs.existsSync(config.planRootDir)) {
    return [];
  }

  const prefix = `${issue.number}-`;
  const planFiles = [];
  for (const entry of fs.readdirSync(config.planRootDir, { withFileTypes: true })) {
    if (!entry.isDirectory() || !entry.name.startsWith(prefix)) {
      continue;
    }
    const planDir = path.join(config.planRootDir, entry.name, PLAN_SUBDIR);
    if (!fs.existsSync(planDir)) {
      continue;
    }
    if (isVisualPlanEnabled(issue)) {
      const dataPath = path.join(planDir, PLAN_DATA_FILE);
      if (fs.existsSync(dataPath)) planFiles.push(normalizeRepoPath(dataPath));
    } else {
      for (const planEntry of fs.readdirSync(planDir, { withFileTypes: true })) {
        if (planEntry.isFile() && planEntry.name.endsWith('.md')) {
          planFiles.push(normalizeRepoPath(path.join(planDir, planEntry.name)));
        }
      }
    }
  }
  return planFiles.sort();
}

function formatIssueLabelsForPrompt(issue) {
  const labels = Array.isArray(issue.labels)
    ? issue.labels.filter((label) => !PROMPT_CONTEXT_LABEL_SKIP_PREFIXES.some((prefix) => label.startsWith(prefix)))
    : [];
  return labels.length > 0 ? labels.join(', ') : '(none)';
}

function formatContextBlock(name, lines) {
  return [`<${name}>`, ...lines, `</${name}>`].join('\n');
}

function formatIssueInput(issue) {
  return [
    `Issue number: #${issue.number}`,
    `Labels: ${formatIssueLabelsForPrompt(issue)}`,
    `Title: ${issue.title || '(untitled)'}`,
    '',
    'Body:',
    issue.body || '(empty)',
  ].join('\n');
}

function formatIssueReference(issue) {
  return `Source issue: #${issue.number} — ${issue.title || '(untitled)'}`;
}

function formatTaskInput(issue, inputFiles = []) {
  if (inputFiles.length === 0) {
    return formatContextBlock('task_input', formatIssueInput(issue).split('\n'));
  }
  return formatContextBlock('task_input', [
    formatIssueReference(issue),
    '',
    'Input files:',
    '',
    ...inputFiles.map((file) => `- \`${file}\``),
  ]);
}

function formatReviewTarget(pr, data = {}) {
  const sourceIssueNumber = data.sourceIssueNumber || extractSourceIssueNumberFromPullRequest(pr);
  const lines = [
    `Number: #${pr.number}`,
    `URL: ${pr.htmlUrl || '(unknown)'}`,
  ];
  if (sourceIssueNumber) {
    lines.push(`Source issue: #${sourceIssueNumber}`);
  }
  return formatContextBlock('review_target', lines);
}

function formatRequiredSkills(action = '', issue = {}) {
  const lines = [
    `Read this project-level skill file before acting: \`${normalizeRepoPath(path.join(skillRootDir(), 'SKILL.md'))}\``,
  ];
  if (action === 'plan' && isVisualPlanEnabled(issue)) {
    if (!hasLabel(issue, 'type::optimization')) {
      lines.push('', `Read and follow the visual plan skill: \`${normalizeRepoPath(path.join(skillRootDir(), '..', 'vision-plan', 'SKILL.md'))}\``);
    }
  }
  if (action === 'plan' && hasLabel(issue, 'type::optimization')) {
    lines.push('', `Read and follow the automation optimizer skill: \`${normalizeRepoPath(path.join(skillRootDir(), '..', 'automation-optimizer', 'SKILL.md'))}\``);
  }
  return formatContextBlock('required_skills', lines);
}

function formatProjectInstructions(options = {}) {
  const { projectInstructionsPath } = resolveAgentrixConfig(options);
  if (!fs.existsSync(projectInstructionsPath)) {
    return '';
  }
  const body = fs.readFileSync(projectInstructionsPath, 'utf8').trim();
  if (!body) {
    return '';
  }
  return formatContextBlock('project_instructions', [
    '以下内容由 Issue Flow 从 `.issue-flow/instructions.md` 直接注入，适用于当前仓库。',
    '若与默认阶段 Prompt、公共 Skill 或其他项目执行说明冲突，优先遵循本项目说明。',
    '',
    body,
  ]);
}

function normalizeBranchName(value) {
  const branch = String(value || '').trim();
  if (!branch || branch === 'null' || branch === 'undefined') {
    return '';
  }
  return branch;
}

function resolvePromptBaseBranch(data = {}, options = {}) {
  const payload = data.payload || {};
  return normalizeBranchName(
    options.base ||
    data.baseRef ||
    data.pullRequest?.baseRef ||
    payload.pull_request?.base?.ref ||
    payload.object_attributes?.target_branch ||
    payload.project?.default_branch ||
    payload.repository?.default_branch ||
    process.env.CI_DEFAULT_BRANCH ||
    process.env.GITHUB_BASE_REF ||
    process.env.GITLAB_BRIDGE_WORKFLOW_RUN_REF ||
    process.env.AGENTRIX_REF
  );
}

function formatRepositoryContext(issue, action, data = {}, options = {}) {
  const baseBranch = resolvePromptBaseBranch(data, options);
  const lines = [];
  if (baseBranch) {
    lines.push(`Base branch: \`${baseBranch}\``);
  }
  lines.push(`Working branch: \`${issueActionBranch(issue, action)}\``);
  return formatContextBlock('repository_context', lines);
}

function formatReviewSubmission(pr) {
  return formatContextBlock('review_submission', [
    'Write the review body to a repo-external temp file.',
    'If a finding is tied to a specific changed file and line, also write inline review comments to a repo-external JSON file.',
    'The inline comments file must be a JSON array like: `[{"path":"src/file.js","line":12,"body":"Comment"}]`.',
    'Submit exactly once. Use the inline comments file when there are concrete findings; add `--as-comment` when there are no findings.',
    '',
    `Command: \`node ${normalizeRepoPath(path.join(skillRootDir(), 'cli.cjs'))} pr review --pr ${pr.number} --body-file TMP_REVIEW_BODY_FILE [--comments-file TMP_INLINE_COMMENTS_JSON] [--as-comment]\``,
  ]);
}

function formatOutputContext(kind, issue, options = {}) {
  if (kind !== 'plan') {
    return '';
  }
  const lines = [];
  if (!isVisualPlanEnabled(issue)) {
    const template = resolvePlanTemplate(issue, options);
    lines.push(`Plan template: \`${normalizeRepoPath(template.path)}\``);
    lines.push(`Plan output file: \`${normalizeRepoPath(buildIssuePlanFile(issue, options))}\``);
    return formatContextBlock('output_context', lines);
  }
  if (hasLabel(issue, 'type::optimization')) {
    lines.push(`Optimization output JSON: \`${normalizeRepoPath(buildIssuePlanFile(issue, options))}\``);
    lines.push(`Publish Optimization Plan: \`node ${normalizeRepoPath(path.join(skillRootDir(), 'cli.cjs'))} pr submit plan --issue ${issue.number} --artifact optimization\``);
    return formatContextBlock('output_context', lines);
  }
  lines.push(`Optional decision output: \`${normalizeRepoPath(buildIssueDecisionFile(issue, options))}\``);
  lines.push(`Plan output JSON: \`${normalizeRepoPath(buildIssuePlanFile(issue, options))}\``);
  lines.push(`Temporary visual brief (do not commit): \`${buildIssuePlanBriefFile(issue).replace(/\\/g, '/')}\``);
  lines.push('If a blocking decision is required, publish `decision` and stop. Otherwise publish `plan`.');
  lines.push(`Publish Decision: \`node ${normalizeRepoPath(path.join(skillRootDir(), 'cli.cjs'))} pr submit plan --issue ${issue.number} --artifact decision\``);
  lines.push(`Publish Plan: \`node ${normalizeRepoPath(path.join(skillRootDir(), 'cli.cjs'))} pr submit plan --issue ${issue.number} --artifact plan\``);

  return formatContextBlock('output_context', lines);
}

function formatAdditionalInstruction(instruction) {
  return instruction ? formatContextBlock('additional_instruction', [instruction]) : '';
}

function composeActionPrompt(action, issue, data = {}, options = {}) {
  if (action === 'review') {
    return composeReviewPrompt(issue, data, options);
  }

  const prompt = readPrompt(action, issue, options);
  const policy = ACTION_CONTEXT_POLICIES[action];
  if (!policy) {
    throw new Error(`Missing prompt context policy for action: ${action}`);
  }
  const inputFiles = policy.input === 'plan-or-issue' && prompt.name === 'build'
    ? listPlanInputFiles(issue, options)
    : [];
  const blocks = [
    prompt.body,
    formatRequiredSkills(action, issue),
    formatProjectInstructions(options),
    formatTaskInput(issue, inputFiles),
  ];
  if (policy.repository) {
    blocks.push(formatRepositoryContext(issue, action, data, options));
  }
  if (policy.output) {
    blocks.push(formatOutputContext(policy.output, issue, options));
  }
  blocks.push(formatAdditionalInstruction(data.instruction));
  return blocks.filter(Boolean).join('\n\n');
}

function extractSourceIssueNumberFromPullRequest(pr = {}) {
  const markerIssueNumber = sourceIssueNumber(pr.body);
  if (markerIssueNumber) return markerIssueNumber;
  const candidates = [
    pr.body,
    pr.title,
    pr.headRef,
    pr.baseRef,
  ].filter(Boolean).map(String);

  const patterns = [
    /Source issue:\s*#(\d+)/i,
    /\b(?:Plan|Build)\s+#(\d+)/i,
    /^(\d+)-[^/]+\/(?:plan|build)$/i,
  ];

  for (const candidate of candidates) {
    for (const pattern of patterns) {
      const match = candidate.match(pattern);
      if (match) {
        return Number.parseInt(match[1], 10);
      }
    }
  }
  return undefined;
}

function extractAgentrixTaskIdFromPullRequest(pr = {}) {
  const source = parseSourceMarker(pr.body || '');
  return source.source_runtime === 'agentrix' ? String(source.source_task_id || '').trim() : '';
}

function buildReviewCommentResumeInstruction() {
  return [
    'PR/MR 有新的 review comment，请查看并处理。',
    '',
    '处理完成后，请使用 issue-flow CLI 在 PR/MR 下回复一条普通总结 comment；不要创建新的 inline review comment。',
  ].join('\n');
}

function buildIssueCommentResumeInstruction() {
  return 'Issue 有新的 comment，请查看并继续处理。';
}

function buildDecisionMergeResumeInstruction(transition = {}) {
  return [
    'Decision 已批准，请继续当前 Plan task，生成并提交 Plan。',
    '',
    `Source issue: #${transition.issueNumber || ''}`,
    '先将当前 Plan 分支同步到默认分支的最新状态，再生成 Plan 产物。',
    '完成后按照 issue-flow 与 vision-plan skill 提交 Plan；不要创建新的 Agentrix task。',
  ].join('\n');
}

function buildReviewResumeInstruction() {
  return 'PR/MR 有新的提交，请继续 review 最新变更。';
}

function composeReviewPrompt(pr, data = {}, options = {}) {
  const prompt = readPrompt('review', pr, options);
  return [
    prompt.body,
    formatRequiredSkills(),
    formatProjectInstructions(options),
    formatReviewTarget(pr, data),
    formatReviewSubmission(pr),
    formatAdditionalInstruction(data.instruction),
  ].filter(Boolean).join('\n\n');
}

function extractMention(body) {
  if (typeof body !== 'string') {
    return {
      triggered: false,
      instruction: '',
    };
  }

  if (!MENTION_PATTERN.test(body)) {
    return {
      triggered: false,
      instruction: '',
    };
  }

  const instruction = body
    .replace(MENTION_REPLACE_PATTERN, ' ')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/^[\s:：,，;；-]+/, '')
    .trim();

  return {
    triggered: true,
    instruction,
  };
}

function truncate(value, maxLength) {
  if (!value || value.length <= maxLength) {
    return value || '';
  }
  return `${value.slice(0, maxLength - 3)}...`;
}

function resolveAgent(options = {}) {
  return options.agent || process.env.AGENTRIX_ISSUE_FLOW_AGENT || process.env.AGENTRIX_AGENT || DEFAULT_AGENT;
}

function resolveResponseMode(options = {}) {
  return options.responseMode || process.env.AGENTRIX_RESPONSE_MODE || DEFAULT_RESPONSE_MODE;
}

function resolveAgentrixRunPackage() {
  const version = process.env.AGENTRIX_RUN_VERSION || DEFAULT_AGENTRIX_RUN_VERSION;
  return `@agentrix/agentrix-run@${version}`;
}

function appendOptionalArg(args, flag, value) {
  if (value) {
    args.push(flag, value);
  }
}

function compactId(value, fallback = '') {
  const normalized = String(value || '').trim();
  if (!normalized) return fallback;
  return normalized.replace(/[^A-Za-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 32) || fallback;
}

function firstNumericId(...values) {
  for (const value of values) {
    const normalized = String(value || '').trim();
    if (/^\d+$/.test(normalized)) {
      return normalized;
    }
  }
  return '';
}

function hostFromUrl(value) {
  try {
    return new URL(String(value || '')).host;
  } catch {
    return '';
  }
}

function resolveClientTaskServerId(options = {}, issue = {}) {
  return compactId(
    options.serverHost ||
    process.env.CI_SERVER_HOST ||
    hostFromUrl(options.serverUrl || process.env.CI_SERVER_URL || issue.htmlUrl) ||
    options.gitServerId ||
    issue.gitServerId ||
    process.env.AGENTRIX_GIT_SERVER_ID,
    '0'
  );
}

function resolveClientTaskRepoId(options = {}, issue = {}) {
  return firstNumericId(
    issue.projectId,
    issue.serverRepoId,
    options.gitlabProject,
    options.serverRepoId,
    options.projectId,
    process.env.CI_PROJECT_ID
  ) || compactId(issue.repoFullName || options.repo, '0');
}

function buildClientTaskId(action, issue = {}, options = {}, data = {}) {
  const actionKey = CLIENT_TASK_ACTIONS[action];
  if (!actionKey || !issue.number) {
    return '';
  }
  const provider = CLIENT_TASK_PROVIDERS[issue.provider] || CLIENT_TASK_PROVIDERS[options.provider] || compactId(issue.provider || options.provider, 'p');
  const serverId = resolveClientTaskServerId(options, issue);
  const repoId = resolveClientTaskRepoId(options, issue);
  if (action === 'review') {
    const head = compactId(issue.headSha || data.headSha || data.pullRequest && data.pullRequest.headSha);
    return head ? `if:${provider}:s${serverId}:r${repoId}:pr${issue.number}:h${head.slice(0, 12)}:${actionKey}` : '';
  }
  if (action === 'general') {
    const commentId = compactId(data.comment && data.comment.id);
    return commentId ? `if:${provider}:s${serverId}:r${repoId}:i${issue.number}:c${commentId}:${actionKey}` : '';
  }
  return `if:${provider}:s${serverId}:r${repoId}:i${issue.number}:${actionKey}`;
}

function shellQuote(value) {
  const text = String(value);
  if (/^[A-Za-z0-9_./:@%+=,-]+$/.test(text)) {
    return text;
  }
  return `'${text.replace(/'/g, `'\\''`)}'`;
}

function redactedCommand(command, args) {
  const redacted = [command];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    redacted.push(arg);
    if (arg === '--api-key' && index + 1 < args.length) {
      redacted.push('[redacted]');
      index += 1;
    }
  }
  return redacted.map(shellQuote).join(' ');
}

function logAgentrixRunCommand(args) {
  console.log(`[issue-flow] Running Agentrix command: ${redactedCommand('npx', args)}`);
}

function buildAgentrixRepo(fullName, gitServerId) {
  const parts = String(fullName || '').split('/').filter(Boolean);
  if (parts.length < 2) {
    throw new Error(`Invalid repository fullName: ${fullName}`);
  }
  return {
    gitServerId,
    owner: parts.slice(0, -1).join('/'),
    name: parts[parts.length - 1],
  };
}

function buildRunTitle(action, issue) {
  if (action === 'review') {
    return `Review PR #${issue.number}: ${truncate(issue.title || 'untitled pull request', 80)}`;
  }
  const actionTitle = action === 'general' ? 'General' : action[0].toUpperCase() + action.slice(1);
  return `${actionTitle} #${issue.number}: ${truncate(issue.title || 'untitled issue', 80)}`;
}

function buildRepoArg(issue = {}, options = {}) {
  const gitServerId = String(options.gitServerId || process.env.AGENTRIX_GIT_SERVER_ID || '').trim();
  if (!gitServerId) {
    return '';
  }
  const owner = String(issue.owner || '').trim();
  const name = String(issue.repo || '').trim();
  const repo = owner && name
    ? { gitServerId, owner, name }
    : buildAgentrixRepo(String(issue.repoFullName || options.repo || '').trim(), gitServerId);
  return JSON.stringify({
    ...repo,
    serverRepoId: options.gitlabProject ? String(options.gitlabProject) : undefined,
  });
}

function buildRunArgs(action, issue, options = {}, data = {}, prompt = '', resultFile = '') {
  const metadataSubject = action === 'review'
    ? ['--metadata', `issue_flow_pr=${issue.repoFullName}#${issue.number}`]
    : ['--issue-number', String(issue.number), '--metadata', `issue_flow_issue=${issue.repoFullName}#${issue.number}`];
  const sourceIssueNumber = data.sourceIssueNumber || (action === 'review' ? extractSourceIssueNumberFromPullRequest(issue) : undefined);
  const args = [
    '--yes',
    resolveAgentrixRunPackage(),
    '--agent',
    resolveAgent(options),
    '--title',
    buildRunTitle(action, issue),
    '--prompt',
    prompt,
    '--response-mode',
    resolveResponseMode(options),
    '--result-file',
    resultFile,
    ...metadataSubject,
    '--metadata',
    `issue_flow_action=${action}`,
  ];
  if (action === 'review' && sourceIssueNumber) {
    args.push('--issue-number', String(sourceIssueNumber));
  }
  if (sourceIssueNumber) {
    args.push('--metadata', `issue_flow_source_issue=${issue.repoFullName}#${sourceIssueNumber}`);
  }

  appendOptionalArg(args, '--client-task-id', buildClientTaskId(action, issue, options, data));
  appendOptionalArg(args, '--base-url', options.baseUrl || process.env.AGENTRIX_BASE_URL);
  appendOptionalArg(args, '--api-key', options.apiKey || process.env.AGENTRIX_API_KEY);
  appendOptionalArg(args, '--repo', buildRepoArg(issue, options));
  appendOptionalArg(args, '--base-ref', resolvePromptBaseBranch(data, options));
  appendOptionalArg(args, '--checkout-ref', data.checkoutRef);
  appendOptionalArg(args, '--runner-id', options.runnerId || process.env.AGENTRIX_RUNNER_ID);
  return args;
}

function buildResumeTaskArgs(taskId, instruction, options = {}, data = {}, resultFile = '') {
  const args = [
    '--yes',
    resolveAgentrixRunPackage(),
    '--resume',
    String(taskId),
    '--prompt',
    instruction,
    '--response-mode',
    resolveResponseMode(options),
    '--result-file',
    resultFile,
  ];
  const pr = data.pullRequest || {};
  if (pr.repoFullName && pr.number) {
    args.push('--metadata', `issue_flow_pr=${pr.repoFullName}#${pr.number}`);
  }
  const reviewComment = data.reviewComment || {};
  if (reviewComment.id) {
    args.push('--metadata', `issue_flow_review_comment=${reviewComment.id}`);
  }
  if (data.sourceIssueNumber && pr.repoFullName) {
    args.push('--metadata', `issue_flow_source_issue=${pr.repoFullName}#${data.sourceIssueNumber}`);
  }

  appendOptionalArg(args, '--base-url', options.baseUrl || process.env.AGENTRIX_BASE_URL);
  appendOptionalArg(args, '--api-key', options.apiKey || process.env.AGENTRIX_API_KEY);
  return args;
}

function clearMergedPullRequestContext(env) {
  for (const key of GITHUB_TRIGGER_ENV_KEYS) {
    delete env[key];
  }
  for (const key of GITLAB_TRIGGER_ENV_KEYS) {
    delete env[key];
  }
  for (const key of Object.keys(env)) {
    if (key.startsWith('GITLAB_BRIDGE_') || key.startsWith('CI_MERGE_REQUEST_')) {
      delete env[key];
    }
  }
}

function sanitizeAgentrixRunEnv(env = process.env, data = {}) {
  const childEnv = { ...env };
  for (const key of PROVIDER_TOKEN_ENV_KEYS) {
    delete childEnv[key];
  }
  if (data.prMerged) {
    clearMergedPullRequestContext(childEnv);
  }
  return childEnv;
}

function run(action, issue, options = {}, data = {}) {
  const prompt = composeActionPrompt(action, issue, data, options);
  if (options.dryRun) {
    const result = {
      runId: 'dry-run',
      status: 'dry-run',
      detailUrl: '',
      result: '',
    };
    console.log(JSON.stringify({ dryRun: true, runtime: 'agentrix', action, subject: issue.number, prompt }, null, 2));
    return result;
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'issue-flow-agentrix-'));
  const resultFile = path.join(tempDir, 'result.json');
  const args = buildRunArgs(action, issue, options, data, prompt, resultFile);
  logAgentrixRunCommand(args);

  const child = spawnSync('npx', args, {
    stdio: 'inherit',
    env: sanitizeAgentrixRunEnv(process.env, data),
  });

  try {
    if (child.status !== 0) {
      throw new Error(`agentrix-run exited with status ${child.status ?? 1}`);
    }
    return JSON.parse(fs.readFileSync(resultFile, 'utf8'));
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

function resumeTask(taskId, instruction, options = {}, data = {}) {
  if (options.dryRun) {
    const result = {
      runId: 'dry-run',
      status: 'dry-run',
      detailUrl: '',
      result: '',
      taskId: String(taskId),
    };
    console.log(JSON.stringify({ dryRun: true, runtime: 'agentrix', action: 'task_resume', taskId: String(taskId), prompt: instruction }, null, 2));
    return result;
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'issue-flow-agentrix-'));
  const resultFile = path.join(tempDir, 'result.json');
  const args = buildResumeTaskArgs(taskId, instruction, options, data, resultFile);
  logAgentrixRunCommand(args);

  const child = spawnSync('npx', args, {
    stdio: 'inherit',
    env: sanitizeAgentrixRunEnv(),
  });

  try {
    if (child.status !== 0) {
      throw new Error(`agentrix-run exited with status ${child.status ?? 1}`);
    }
    return JSON.parse(fs.readFileSync(resultFile, 'utf8'));
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

function firstPresent(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return value;
    }
  }
  return '';
}

function buildReviewCommentResumeKey(reviewComment = {}) {
  const raw = reviewComment.raw || {};
  const reviewId = firstPresent(
    reviewComment.reviewId,
    reviewComment.pullRequestReviewId,
    reviewComment.pull_request_review_id,
    raw.pull_request_review_id,
    raw.reviewId,
    raw.pullRequestReviewId
  );
  if (reviewId) {
    return `review:${reviewId}`;
  }
  return firstPresent(reviewComment.id, reviewComment.htmlUrl, 'unknown');
}

function buildTaskCommentMarker(action, data = {}) {
  if (action === 'task_resume' && data.reviewComment) {
    const key = buildReviewCommentResumeKey(data.reviewComment);
    return `<!-- issue-flow:agentrix:task:resume-review-comment:${String(key).replace(/\s+/g, '-')} -->`;
  }
  return `<!-- issue-flow:agentrix:task:${action} -->`;
}

function buildTaskComment(action, result, data = {}) {
  const pr = data.pullRequest || data;
  const displayedAction = action === 'task_resume'
    ? firstPresent(data.issueTask && data.issueTask.action, data.taskAction, action)
    : action;
  const lines = [buildTaskCommentMarker(action, data)];
  const sourceMarker = buildSourceMarker({
    sourceTaskId: result.runId || data.agentrixTaskId,
    sourceAgent: data.sourceAgent || data.agent || DEFAULT_AGENT,
    sourceRuntime: 'agentrix',
  });
  if (sourceMarker) {
    lines.push(sourceMarker);
  }
  if (action === 'review') {
    if (result.status === 'starting') {
      lines.push('Agentrix review starting. This comment prevents duplicate issue-flow reviews for this PR/MR.');
    } else if (result.detailUrl) {
      lines.push(`Agentrix review queued: [open task](${result.detailUrl}).`);
    } else {
      lines.push('Agentrix review queued.');
    }
    lines.push('');
    if (result.runId) {
      lines.push(`- Review task: \`${result.runId}\``);
    }
    const buildTaskId = data.agentrixTaskId || extractAgentrixTaskIdFromPullRequest(pr);
    if (buildTaskId) {
      lines.push(`- Build task: \`${buildTaskId}\``);
    }
    if (pr && pr.headSha) {
      lines.push(`- Head: \`${pr.headSha}\``);
    }
    return lines.join('\n');
  }

  if (result.status === 'starting') {
    if (action === 'task_resume') {
      lines.push('Agentrix task resume starting. This comment prevents duplicate issue-flow resumes for the same PR/MR review comment.');
    } else {
      lines.push(`Agentrix task starting. This comment prevents duplicate issue-flow tasks for the same ${action === 'review' ? 'PR/MR' : 'issue'}/action.`);
    }
  } else if (result.detailUrl) {
    lines.push(`Agentrix task queued: [open task](${result.detailUrl}).`);
  } else {
    lines.push('Agentrix task queued.');
  }
  lines.push('');
  lines.push(`- Action: \`${displayedAction}\``);
  if (result.runId) {
    lines.push(`- Run: \`${result.runId}\``);
  }
  if (action === 'review' && pr && pr.headSha) {
    lines.push(`- Head: \`${pr.headSha}\``);
  }
  if (data.comment && data.comment.htmlUrl) {
    lines.push(`- Trigger: ${data.comment.htmlUrl}`);
  } else if (data.reviewComment && data.reviewComment.htmlUrl) {
    lines.push(`- Trigger: ${data.reviewComment.htmlUrl}`);
  } else if (data.pullRequest) {
    lines.push('- Trigger: PR/MR review check.');
  } else if (data.auto) {
    lines.push('- Trigger: automatic issue-flow.');
  }
  if (data.reviewComment && data.reviewComment.id) {
    lines.push(`- Review comment: \`${data.reviewComment.id}\``);
  }
  if (data.reviewComment && data.reviewComment.reviewId) {
    lines.push(`- Review batch: \`${data.reviewComment.reviewId}\``);
  }
  if (data.agentrixTaskId && action !== 'task_resume') {
    lines.push(`- Agentrix task: \`${data.agentrixTaskId}\``);
  }
  return lines.join('\n');
}

function extractRunIdFromTaskComment(comment) {
  const body = typeof comment === 'string' ? comment : (comment && comment.body) || '';
  const match = body.match(TASK_COMMENT_RUN_PATTERN);
  if (match) {
    return match[1].trim();
  }
  const reviewTaskMatch = body.match(REVIEW_COMMENT_TASK_PATTERN);
  return reviewTaskMatch ? reviewTaskMatch[1].trim() : '';
}

function extractIssueTaskFromTaskComment(comment) {
  const body = typeof comment === 'string' ? comment : (comment && comment.body) || '';
  const marker = body.match(ISSUE_TASK_COMMENT_MARKER_PATTERN);
  if (!marker) {
    return undefined;
  }
  const taskId = extractRunIdFromTaskComment(body);
  if (!taskId) {
    return undefined;
  }
  const detailUrlMatch = body.match(TASK_COMMENT_DETAIL_URL_PATTERN);
  return {
    action: marker[1],
    taskId,
    commentId: comment && comment.id !== undefined ? String(comment.id) : '',
    commentUrl: String(comment && (comment.html_url || comment.htmlUrl || comment.web_url || comment.url) || ''),
    ...(detailUrlMatch ? { detailUrl: detailUrlMatch[1] } : {}),
  };
}

function extractReviewHeadShaFromTaskComment(comment) {
  const body = typeof comment === 'string' ? comment : (comment && comment.body) || '';
  const headMatch = body.match(REVIEW_COMMENT_HEAD_PATTERN);
  return headMatch ? headMatch[1].trim() : '';
}

function shouldAcknowledgeAutoIssue(action, data = {}) {
  return action === 'triage' && data.auto === true;
}

module.exports = {
  DEFAULT_MENTION,
  SUPPORTED_ACTIONS,
  buildIssueDecisionFile,
  buildIssuePlanBriefFile,
  buildIssuePlanDataFile,
  buildIssuePlanFile,
  composeActionPrompt,
  composeReviewPrompt,
  buildAgentrixRepo,
  buildIssueCommentResumeInstruction,
  buildDecisionMergeResumeInstruction,
  buildClientTaskId,
  buildResumeTaskArgs,
  buildRunArgs,
  sanitizeAgentrixRunEnv,
  buildReviewCommentResumeInstruction,
  buildReviewResumeInstruction,
  buildReviewCommentResumeKey,
  buildTaskComment,
  buildTaskCommentMarker,
  extractAgentrixTaskIdFromPullRequest,
  extractIssueTaskFromTaskComment,
  parseSourceMarker,
  extractMention,
  extractReviewHeadShaFromTaskComment,
  extractRunIdFromTaskComment,
  extractSourceIssueNumberFromPullRequest,
  issueActionBranch,
  issueDirectoryName,
  isVisualPlanEnabled,
  listPlanInputFiles,
  normalizeRepoPath,
  resolveAgentrixConfig,
  resolvePlanTemplate,
  visualPlanFeatureMode,
  resolvePromptBaseBranch,
  redactedCommand,
  run,
  resumeTask,
  shellQuote,
  shouldAcknowledgeAutoIssue,
};
