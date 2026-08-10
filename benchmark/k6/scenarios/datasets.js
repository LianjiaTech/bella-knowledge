import {
  createQa,
  expectSuccess,
  postJson,
  unique,
} from '../lib/client.js';
import { loadOptions, numberEnv } from '../lib/config.js';
import { seedQaDataset } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

export const options = loadOptions();

export function setup() {
  return seedQaDataset(numberEnv('DATASET_SEED_QAS', 30), unique('datasets'));
}

export default function (fixture) {
  const roll = Math.random();

  if (roll < 0.4) {
    expectSuccess(postJson('/v1/datasets/qa/page', {
      dataset_id: fixture.datasetId,
      page: Math.random() < 0.8 ? 1 : Math.max(1, Math.ceil(fixture.itemIds.length / 20)),
      page_size: 20,
      order: 'desc',
      order_by: 'ctime',
    }, 'POST /v1/datasets/qa/page'), 'page QA', (value) => value && Array.isArray(value.data));
    return;
  }

  if (roll < 0.65) {
    const itemId = fixture.itemIds[Math.floor(Math.random() * fixture.itemIds.length)];
    expectSuccess(postJson('/v1/datasets/qa/get', {
      dataset_id: fixture.datasetId,
      item_id: itemId,
    }, 'POST /v1/datasets/qa/get'), 'get QA', (value) => value && value.item_id === itemId);
    return;
  }

  if (roll < 0.8) {
    expectSuccess(postJson('/v1/datasets/page', {
      page: 1,
      page_size: 30,
      order: 'desc',
      order_by: 'ctime',
    }, 'POST /v1/datasets/page'), 'page datasets', (value) => value && Array.isArray(value.data));
    return;
  }

  expectSuccess(createQa(fixture.datasetId, unique('load-qa')), 'create QA', (value) => value && value.item_id);
}

export { handleSummary };
