import { createDirectory, expectSuccess, mustSucceed, postJson, unique } from '../lib/client.js';
import { VUS, loadOptions, numberEnv } from '../lib/config.js';
import { seedDirectories } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

export const options = loadOptions();

function deepTarget(parentId, depth, prefix) {
  let currentId = parentId;
  for (let level = 0; level < depth; level += 1) {
    const directory = mustSucceed(
      createDirectory(`${prefix}-depth-${level}`, currentId),
      'seed move target',
      (value) => Boolean(value && value.id),
    );
    currentId = directory.id;
  }
  return currentId;
}

export function setup() {
  const workerCount = Math.max(numberEnv('MOVE_WORKERS', VUS), VUS);
  const subtreeSize = numberEnv('MOVE_SUBTREE_SIZE', 10);
  const targetDepth = numberEnv('MOVE_TARGET_DEPTH', 3);
  const workspace = mustSucceed(createDirectory(unique('move-workspace')), 'create move workspace', (value) => Boolean(value && value.id));
  const workers = [];

  for (let worker = 0; worker < workerCount; worker += 1) {
    const prefix = unique(`move-${worker}`);
    const targetA = deepTarget(workspace.id, targetDepth, `${prefix}-a`);
    const targetB = deepTarget(workspace.id, targetDepth, `${prefix}-b`);
    const movingRoot = mustSucceed(createDirectory(`${prefix}-subtree`, targetA), 'create moving root', (value) => Boolean(value && value.id));
    seedDirectories(Math.max(0, subtreeSize - 1), movingRoot.id, `${prefix}-child`);
    workers.push({ movingRootId: movingRoot.id, targetA, targetB });
  }
  return { workers };
}

export default function (fixture) {
  const worker = fixture.workers[(__VU - 1) % fixture.workers.length];
  const destination = __ITER % 2 === 0 ? worker.targetB : worker.targetA;
  expectSuccess(postJson('/v1/files/move', {
    file_id: worker.movingRootId,
    ancestor_id: destination,
  }, 'POST /v1/files/move [subtree]'), 'move subtree', (value) => value && value.id === worker.movingRootId);
}

export { handleSummary };
