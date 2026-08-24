import exec from 'k6/execution';
import { Trend } from 'k6/metrics';
import { createDirectory, expectSuccess, get, mustSucceed, postJson, unique } from '../lib/client.js';
import { SPACE_CODE, numberEnv } from '../lib/config.js';
import { seedDirectoryTree } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

const TARGET_SPACE = __ENV.CROSS_SHARD_TARGET_SPACE || 'cross-shard-benchmark';
const SUBTREE_SIZE = numberEnv('CROSS_SHARD_SUBTREE_SIZE', 1000);
const BRANCHING = numberEnv('CROSS_SHARD_BRANCHING', 10);
const WORKERS = numberEnv('CROSS_SHARD_MOVE_WORKERS', 1);
const P95_MS = numberEnv('CROSS_SHARD_P95_MS', 10000);

const moveDuration = new Trend('cross_shard_move_duration', true);

export const options = {
  scenarios: {
    crossShardMove: {
      executor: 'shared-iterations',
      vus: WORKERS,
      iterations: WORKERS,
      maxDuration: '10m',
    },
  },
  setupTimeout: '30m',
  discardResponseBodies: false,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    application_errors: ['rate<0.01'],
    cross_shard_move_duration: [`p(95)<${P95_MS}`],
  },
};

function javaHash(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash * 31) + value.charCodeAt(index)) | 0;
  }
  return hash;
}

function shardOf(spaceCode) {
  return Math.abs(javaHash(spaceCode)) % 16;
}

export function setup() {
  const sourceShard = shardOf(SPACE_CODE);
  const targetShard = shardOf(TARGET_SPACE);
  if (sourceShard === targetShard) {
    throw new Error(`source and target spaces share shard ${sourceShard}: ${SPACE_CODE}, ${TARGET_SPACE}`);
  }

  const workers = [];
  for (let worker = 0; worker < WORKERS; worker += 1) {
    const prefix = unique(`cross-shard-${worker}`);
    const root = mustSucceed(
      createDirectory(`${prefix}-root`),
      'create cross-shard root',
      (value) => Boolean(value && value.id),
    );
    const nodeIds = seedDirectoryTree(SUBTREE_SIZE, root.id, `${prefix}-node`, BRANCHING);
    workers.push({ rootId: root.id, nodeIds });
  }

  return {
    workers,
    sourceShard,
    targetShard,
  };
}

export default function (fixture) {
  const worker = fixture.workers[exec.scenario.iterationInTest];
  const response = postJson('/v1/files/move', {
    file_id: worker.rootId,
    ancestor_id: null,
    target_space_code: TARGET_SPACE,
  }, 'POST /v1/files/move [cross-shard subtree]');
  moveDuration.add(response.timings.duration, {
    source_shard: String(fixture.sourceShard),
    target_shard: String(fixture.targetShard),
    subtree_size: String(SUBTREE_SIZE),
  });
  expectSuccess(response, 'move cross-shard subtree', (value) =>
    value && value.id === worker.rootId && value.space_code === TARGET_SPACE);

  const verification = get(
    `/v1/files/${worker.rootId}`,
    'GET /v1/files/:id [cross-shard verification]',
  );
  expectSuccess(verification, 'verify cross-shard root', (value) =>
    value && value.id === worker.rootId && value.space_code === TARGET_SPACE);

  for (let offset = 0; offset < worker.nodeIds.length; offset += 1000) {
    const batch = worker.nodeIds.slice(offset, offset + 1000);
    const ancestors = postJson('/v1/files/ancestor-ids', {
      space_code: TARGET_SPACE,
      file_ids: batch,
    }, 'POST /v1/files/ancestor-ids [cross-shard verification]');
    expectSuccess(ancestors, 'verify migrated subtree batch', (value) =>
      value && batch.every((fileId) => Array.isArray(value[fileId])));
  }
}

export function teardown(fixture) {
  console.log(`cross-shard move: nodes=${SUBTREE_SIZE}, branching=${BRANCHING}, workers=${WORKERS}, shards=${fixture.sourceShard}->${fixture.targetShard}`);
}

export { handleSummary };
