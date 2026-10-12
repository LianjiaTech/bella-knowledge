const MANAGED_LABEL_GROUPS = {
  type: {
    prefix: 'type::',
    scope: 'issue',
    labels: [
      {
        name: 'type::feature',
        color: '0E8A16',
        description: 'Issue is a feature or enhancement',
      },
      {
        name: 'type::bug',
        color: 'D73A4A',
        description: 'Issue reports a defect or regression',
      },
      {
        name: 'type::debt',
        color: '5319E7',
        description: 'Issue tracks technical debt or cleanup',
      },
      {
        name: 'type::ops',
        color: '1D76DB',
        description: 'Issue tracks operations or maintenance work',
      },
      {
        name: 'type::docs',
        color: 'D97706',
        description: 'Issue adds, revises, migrates, or reorganizes documentation',
      },
      {
        name: 'type::optimization',
        color: '57606A',
        description: 'Issue analyzes and improves automation for a previous task',
      },
    ],
  },
  status: {
    prefix: 'status::',
    scope: 'issue',
    labels: [
      {
        name: 'status::active',
        color: '0052CC',
        description: 'Issue is active and eligible for workflow actions',
      },
      {
        name: 'status::done',
        color: '0E8A16',
        description: 'Issue is complete',
      },
      {
        name: 'status::drop',
        color: '6A737D',
        description: 'Issue has been dropped and should not continue',
      },
      {
        name: 'status::suspend',
        color: 'FBCA04',
        description: 'Issue is suspended until conditions change',
      },
    ],
  },
  flow: {
    prefix: 'flow::',
    scope: 'issue',
    labels: [
      {
        name: 'flow::triage',
        color: 'BFDADC',
        description: 'Waiting for triage',
      },
      {
        name: 'flow::plan',
        color: '0052CC',
        description: 'Waiting for a plan action',
      },
      {
        name: 'flow::build',
        color: '1D76DB',
        description: 'Waiting for implementation',
      },
      {
        name: 'flow::clarify',
        color: 'D4C5F9',
        description: 'Waiting for clarification',
      },
      {
        name: 'flow::approve',
        color: '0E8A16',
        description: 'Waiting for approval of a plan or build PR/MR',
      },
    ],
  },
  visualPlanFeature: {
    prefix: 'feature:visual-plan:',
    scope: 'issue',
    labels: [
      { name: 'feature:visual-plan:on', color: '5319E7', description: 'Use Visual Decision and Visual Plan for this issue' },
    ],
  },
  automation: {
    prefix: 'automation::',
    scope: 'issue',
    labels: [
      {
        name: 'automation::off',
        color: '6A737D',
        description: 'Automation is explicitly disabled for this issue',
      },
      {
        name: 'automation::plan',
        color: '7057FF',
        description: 'Automation may execute the configured plan flow',
      },
      {
        name: 'automation::build',
        color: '006B75',
        description: 'Automation may execute the configured plan flow and create build PRs/MRs',
      },
    ],
  },
  optimizationState: {
    prefix: 'optimization::',
    scope: 'issue',
    labels: [
      {
        name: 'optimization::analyzing',
        color: 'D97706',
        description: 'A linked automation optimization issue is in progress',
      },
      {
        name: 'optimization::analyzed',
        color: '1A7F37',
        description: 'The linked automation optimization issue is complete',
      },
    ],
  },
  priority: {
    prefix: 'priority::',
    scope: 'issue',
    labels: [
      {
        name: 'priority::p0',
        color: 'B60205',
        description: 'Highest priority issue',
      },
      {
        name: 'priority::p1',
        color: 'D93F0B',
        description: 'High priority issue',
      },
      {
        name: 'priority::p2',
        color: 'FBCA04',
        description: 'Normal priority issue',
      },
      {
        name: 'priority::p3',
        color: 'C5DEF5',
        description: 'Low priority issue',
      },
    ],
  },
  size: {
    prefix: 'size::',
    scope: 'issue',
    labels: [
      {
        name: 'size::XS',
        color: 'C2E0C6',
        description: 'Size XS; throughput weight 0.5',
        weight: 0.5,
      },
      {
        name: 'size::S',
        color: 'BFDADC',
        description: 'Size S; throughput weight 1',
        weight: 1,
      },
      {
        name: 'size::M',
        color: 'C5DEF5',
        description: 'Size M; throughput weight 2',
        weight: 2,
      },
      {
        name: 'size::L',
        color: 'D4C5F9',
        description: 'Size L; throughput weight 3',
        weight: 3,
      },
      {
        name: 'size::XL',
        color: 'F9D0C4',
        description: 'Size XL; throughput weight 5',
        weight: 5,
      },
    ],
  },
  'mr-by': {
    prefix: 'mr-by::',
    scope: 'merge_request',
    labels: [
      {
        name: 'mr-by::plan',
        color: '0052CC',
        description: 'PR or MR was created by the plan action',
      },
      {
        name: 'mr-by::build',
        color: '1D76DB',
        description: 'PR or MR was created by the build action',
      },
    ],
  },
  review: {
    prefix: 'review::',
    scope: 'merge_request',
    labels: [
      {
        name: 'review::off',
        color: '6A737D',
        description: 'Automatic review is disabled for this PR or MR',
      },
    ],
  },
};

const MANAGED_LABELS = Object.freeze(
  Object.values(MANAGED_LABEL_GROUPS)
    .flatMap((group) =>
      group.labels.map((label) => ({
        ...label,
        scope: group.scope,
        group: group.prefix.replace(/::$/, ''),
        prefix: group.prefix,
      }))
    )
    .map((label) => Object.freeze(label))
);

const LABEL_BY_NAME = new Map(MANAGED_LABELS.map((label) => [label.name, label]));

function labelsForScope(scope = 'all') {
  if (scope === 'all') {
    return MANAGED_LABELS;
  }
  return MANAGED_LABELS.filter((label) => label.scope === scope);
}

function labelDefinitionFor(name) {
  return LABEL_BY_NAME.get(name);
}

function labelGroupsForScope(scope) {
  const entries = Object.entries(MANAGED_LABEL_GROUPS).filter(([, group]) => !scope || group.scope === scope);
  return Object.fromEntries(
    entries.map(([key, group]) => [
      key,
      {
        prefix: group.prefix,
        scope: group.scope,
        values: group.labels.map((label) => label.name),
      },
    ])
  );
}

function labelsWithPrefix(labels, prefix) {
  if (!Array.isArray(labels)) {
    return [];
  }
  return labels.filter((label) => typeof label === 'string' && label.startsWith(prefix));
}

function findSingleManagedLabel(labels, prefix) {
  const matches = labelsWithPrefix(labels, prefix);
  if (matches.length === 0) {
    return undefined;
  }
  if (matches.length > 1) {
    throw new Error(`Expected one ${prefix} label but found: ${matches.join(', ')}`);
  }
  return matches[0];
}

function resolveIssueSizeLabel(labels) {
  const matches = labelsWithPrefix(labels, 'size::');
  if (matches.length === 0) {
    return {
      ok: false,
      code: 'missing_size_label',
      reason: 'This issue needs exactly one size:: label before it can enter flow::plan or flow::build.',
    };
  }
  if (matches.length > 1) {
    return {
      ok: false,
      code: 'multiple_size_labels',
      reason: 'This issue has more than one size:: label; choose one size label before continuing.',
      labels: matches,
    };
  }
  const definition = labelDefinitionFor(matches[0]);
  if (!definition || definition.prefix !== 'size::' || definition.scope !== 'issue') {
    return {
      ok: false,
      code: 'invalid_size_label',
      reason: 'This issue has a size:: label that is not managed by issue-flow; choose one managed size label before continuing.',
      labels: matches,
    };
  }
  return {
    ok: true,
    label: matches[0],
    weight: definition && definition.weight,
  };
}

function requireSingleIssueSize(labels, context = {}) {
  const result = resolveIssueSizeLabel(labels);
  if (result.ok) {
    return result;
  }

  if (result.code === 'multiple_size_labels') {
    throw new Error(
      [
        `This issue has more than one size:: label: ${result.labels.join(', ')}.`,
        'Choose one size and re-run with --size size::<value>; issue-flow will replace the conflicting size labels.',
      ].join(' ')
    );
  }

  if (result.code === 'invalid_size_label') {
    throw new Error(
      [
        `This issue has an invalid size:: label: ${result.labels.join(', ')}.`,
        'Choose size::XS, size::S, size::M, size::L, or size::XL and re-run with --size size::<value>.',
      ].join(' ')
    );
  }

  const command = context.issueNumber
    ? `issue-flow issue apply --issue ${context.issueNumber} --size size::M`
    : 'issue-flow issue apply --issue <number> --size size::M';
  throw new Error(
    [
      'This issue needs exactly one size:: label before it can enter flow::plan or flow::build.',
      'Choose size::XS, size::S, size::M, size::L, or size::XL and pass --size size::<value>.',
      'If you are unsure, use size::M and leave a low-confidence note.',
      `Next step example: ${command}`,
    ].join(' ')
  );
}

module.exports = {
  MANAGED_LABEL_GROUPS,
  MANAGED_LABELS,
  findSingleManagedLabel,
  labelDefinitionFor,
  labelGroupsForScope,
  labelsWithPrefix,
  labelsForScope,
  requireSingleIssueSize,
  resolveIssueSizeLabel,
};
