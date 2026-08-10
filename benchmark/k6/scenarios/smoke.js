import { group } from 'k6';
import {
  createDataset,
  createDirectory,
  createQa,
  data,
  expectSuccess,
  get,
  mustSucceed,
  postJson,
  unique,
  uploadFile,
} from '../lib/client.js';
import { SPACE_CODE, loadOptions } from '../lib/config.js';
import { handleSummary } from '../lib/summary.js';

const uploadBody = open('/fixtures/upload.bin', 'b');

export const options = {
  vus: 1,
  iterations: 1,
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    application_errors: ['rate==0'],
  },
};

export default function () {
  group('file workflow', () => {
    const root = mustSucceed(createDirectory(unique('smoke-root')), 'create root', (value) => Boolean(value && value.id));
    const target = mustSucceed(createDirectory(unique('smoke-target')), 'create target', (value) => Boolean(value && value.id));
    const file = mustSucceed(
      uploadFile(uploadBody, `${unique('smoke-file')}.bin`, root.id),
      'upload file',
      (value) => Boolean(value && value.id),
    );

    expectSuccess(get(`/v1/files/${file.id}`, 'GET /v1/files/{id}'), 'get file', (value) => value && value.id === file.id);
    expectSuccess(postJson('/v1/files/page', {
      ancestor_id: root.id,
      page: 1,
      page_size: 20,
    }, 'POST /v1/files/page'), 'page files', (value) => value && Array.isArray(value.data));
    expectSuccess(postJson('/v1/files/move', {
      file_id: file.id,
      ancestor_id: target.id,
    }, 'POST /v1/files/move'), 'move file', (value) => value && value.id === file.id);
    expectSuccess(postJson('/v1/files/ancestor-ids', {
      space_code: SPACE_CODE,
      file_ids: [file.id],
    }, 'POST /v1/files/ancestor-ids'), 'get ancestors', (value) => value && Array.isArray(value[file.id]));
  });

  group('dataset workflow', () => {
    const dataset = mustSucceed(createDataset(unique('smoke-dataset')), 'create dataset', (value) => Boolean(value && value.dataset_id));
    const qa = mustSucceed(createQa(dataset.dataset_id, unique('smoke-qa')), 'create QA', (value) => Boolean(value && value.item_id));

    expectSuccess(postJson('/v1/datasets/qa/get', {
      dataset_id: dataset.dataset_id,
      item_id: qa.item_id,
    }, 'POST /v1/datasets/qa/get'), 'get QA', (value) => value && value.item_id === qa.item_id);
    const pageResponse = postJson('/v1/datasets/qa/page', {
      dataset_id: dataset.dataset_id,
      page: 1,
      page_size: 20,
      order: 'desc',
      order_by: 'ctime',
    }, 'POST /v1/datasets/qa/page');
    expectSuccess(pageResponse, 'page QA', (value) => value && Array.isArray(value.data));
    data(pageResponse);
  });
}

export { handleSummary };
