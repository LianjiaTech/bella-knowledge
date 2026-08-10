import {
  createDirectory,
  expectSuccess,
  get,
  mustSucceed,
  postJson,
  unique,
  uploadFile,
} from '../lib/client.js';
import { Trend } from 'k6/metrics';
import { VUS, loadOptions, numberEnv } from '../lib/config.js';
import { seedDirectories } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

const uploadBody = open('/fixtures/upload.bin', 'b');

export const options = loadOptions();
let moveToTargetB = true;

const readDuration = new Trend('mixed_read_duration', true);
const pageDuration = new Trend('mixed_page_duration', true);
const uploadDuration = new Trend('mixed_upload_duration', true);
const mkdirDuration = new Trend('mixed_mkdir_duration', true);
const moveDuration = new Trend('mixed_move_duration', true);

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

  return {
    workspaceId: workspace.id,
    readIds,
    workers,
  };
}

export default function (fixture) {
  const roll = Math.random();
  const worker = fixture.workers[(__VU - 1) % fixture.workers.length];

  if (roll < 0.4) {
    const id = fixture.readIds[Math.floor(Math.random() * fixture.readIds.length)];
    const response = get(`/v1/files/${id}`, 'GET /v1/files/{id} [mixed]');
    readDuration.add(response.timings.duration);
    expectSuccess(response, 'mixed read file', (value) => value && value.id === id);
    return;
  }

  if (roll < 0.6) {
    const response = postJson('/v1/files/page', {
      ancestor_id: fixture.workspaceId,
      page: 1,
      page_size: 50,
    }, 'POST /v1/files/page [mixed]');
    pageDuration.add(response.timings.duration);
    expectSuccess(response, 'mixed page files', (value) => value && Array.isArray(value.data));
    return;
  }

  if (roll < 0.75) {
    const response = uploadFile(uploadBody, `${unique('mixed-upload')}.bin`, fixture.workspaceId, 'POST /v1/files [mixed]');
    uploadDuration.add(response.timings.duration);
    expectSuccess(response, 'mixed upload', (value) => value && value.id);
    return;
  }

  if (roll < 0.875) {
    const response = createDirectory(unique('mixed-mkdir'), fixture.workspaceId);
    mkdirDuration.add(response.timings.duration);
    expectSuccess(response, 'mixed mkdir', (value) => value && value.id);
    return;
  }

  const destination = moveToTargetB ? worker.targetB : worker.targetA;
  const response = postJson('/v1/files/move', {
    file_id: worker.movingId,
    ancestor_id: destination,
  }, 'POST /v1/files/move [mixed]');
  moveDuration.add(response.timings.duration);
  const moved = expectSuccess(response, 'mixed move', (value) => value && value.id === worker.movingId);
  if (moved) {
    moveToTargetB = !moveToTargetB;
  }
}

export { handleSummary };
