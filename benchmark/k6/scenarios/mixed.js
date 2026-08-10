import {
  createDirectory,
  createQa,
  expectSuccess,
  get,
  mustSucceed,
  postJson,
  unique,
  uploadFile,
} from '../lib/client.js';
import { VUS, loadOptions, numberEnv } from '../lib/config.js';
import { seedDirectories, seedQaDataset } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

const uploadBody = open('/fixtures/upload.bin', 'b');

export const options = loadOptions();
let moveToTargetB = true;

export function setup() {
  const workerCount = Math.max(numberEnv('MIXED_WORKERS', VUS), VUS);
  const workspace = mustSucceed(createDirectory(unique('mixed-workspace')), 'create mixed workspace', (value) => Boolean(value && value.id));
  const readIds = seedDirectories(Math.max(20, workerCount * 5), workspace.id, unique('mixed-read'));
  const workers = [];

  for (let index = 0; index < workerCount; index += 1) {
    const targetA = mustSucceed(createDirectory(unique(`mixed-a-${index}`), workspace.id), 'create mixed target A', (value) => Boolean(value && value.id));
    const targetB = mustSucceed(createDirectory(unique(`mixed-b-${index}`), workspace.id), 'create mixed target B', (value) => Boolean(value && value.id));
    const moving = mustSucceed(createDirectory(unique(`mixed-moving-${index}`), targetA.id), 'create mixed moving dir', (value) => Boolean(value && value.id));
    workers.push({ targetA: targetA.id, targetB: targetB.id, movingId: moving.id });
  }

  const dataset = seedQaDataset(numberEnv('DATASET_SEED_QAS', 30), unique('mixed'));
  return {
    workspaceId: workspace.id,
    readIds,
    workers,
    datasetId: dataset.datasetId,
    itemIds: dataset.itemIds,
  };
}

export default function (fixture) {
  const roll = Math.random();
  const worker = fixture.workers[(__VU - 1) % fixture.workers.length];

  if (roll < 0.3) {
    const id = fixture.readIds[Math.floor(Math.random() * fixture.readIds.length)];
    expectSuccess(get(`/v1/files/${id}`, 'GET /v1/files/{id} [mixed]'), 'mixed read file', (value) => value && value.id === id);
    return;
  }

  if (roll < 0.45) {
    expectSuccess(postJson('/v1/files/page', {
      ancestor_id: fixture.workspaceId,
      page: 1,
      page_size: 50,
    }, 'POST /v1/files/page [mixed]'), 'mixed page files', (value) => value && Array.isArray(value.data));
    return;
  }

  if (roll < 0.58) {
    expectSuccess(uploadFile(uploadBody, `${unique('mixed-upload')}.bin`, fixture.workspaceId, 'POST /v1/files [mixed]'),
      'mixed upload', (value) => value && value.id);
    return;
  }

  if (roll < 0.68) {
    expectSuccess(createDirectory(unique('mixed-mkdir'), fixture.workspaceId), 'mixed mkdir', (value) => value && value.id);
    return;
  }

  if (roll < 0.78) {
    const destination = moveToTargetB ? worker.targetB : worker.targetA;
    const moved = expectSuccess(postJson('/v1/files/move', {
      file_id: worker.movingId,
      ancestor_id: destination,
    }, 'POST /v1/files/move [mixed]'), 'mixed move', (value) => value && value.id === worker.movingId);
    if (moved) {
      moveToTargetB = !moveToTargetB;
    }
    return;
  }

  if (roll < 0.93) {
    expectSuccess(postJson('/v1/datasets/qa/page', {
      dataset_id: fixture.datasetId,
      page: 1,
      page_size: 20,
      order: 'desc',
      order_by: 'ctime',
    }, 'POST /v1/datasets/qa/page [mixed]'), 'mixed page QA', (value) => value && Array.isArray(value.data));
    return;
  }

  expectSuccess(createQa(fixture.datasetId, unique('mixed-qa')), 'mixed create QA', (value) => value && value.item_id);
}

export { handleSummary };
