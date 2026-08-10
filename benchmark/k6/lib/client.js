import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, AUTH_TOKEN } from './config.js';

export const applicationErrors = new Rate('application_errors');

const authHeaders = {
  Authorization: `Bearer ${AUTH_TOKEN}`,
};

export function get(path, name) {
  return http.get(`${BASE_URL}${path}`, {
    headers: authHeaders,
    tags: { name },
  });
}

export function postJson(path, body, name) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: {
      Authorization: authHeaders.Authorization,
      'Content-Type': 'application/json',
    },
    tags: { name },
  });
}

export function putJson(path, body, name) {
  return http.put(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: {
      Authorization: authHeaders.Authorization,
      'Content-Type': 'application/json',
    },
    tags: { name },
  });
}

export function uploadFile(fileBody, filename, ancestorId, name = 'POST /v1/files') {
  const form = {
    file: http.file(fileBody, filename, 'application/octet-stream'),
    purpose: 'assistants',
  };
  if (ancestorId) {
    form.ancestor_id = ancestorId;
  }
  return http.post(`${BASE_URL}/v1/files`, form, {
    headers: authHeaders,
    tags: { name },
    timeout: __ENV.UPLOAD_TIMEOUT || '2m',
  });
}

export function parse(res) {
  try {
    return res.json();
  } catch (_) {
    return null;
  }
}

export function data(res) {
  const body = parse(res);
  if (body && body.code !== undefined && body.data !== undefined) {
    return body.data;
  }
  return body;
}

export function expectSuccess(res, label, validate = () => true) {
  const body = parse(res);
  const applicationSuccess = !body || body.code === undefined || body.code === 200;
  const ok = check(res, {
    [`${label}: status/application code is successful`]: (response) =>
      response.status >= 200 && response.status < 300 && applicationSuccess,
    [`${label}: response shape is valid`]: () => validate(data(res)),
  });
  applicationErrors.add(!ok, { operation: label });
  return ok;
}

export function mustSucceed(res, label, validate = () => true) {
  if (!expectSuccess(res, label, validate)) {
    fail(`${label} failed: HTTP ${res.status}, body=${res.body}`);
  }
  return data(res);
}

export function unique(prefix) {
  const vu = typeof __VU === 'undefined' ? 0 : __VU;
  const iteration = typeof __ITER === 'undefined' ? 0 : __ITER;
  return `${prefix}-${Date.now()}-${vu}-${iteration}-${Math.floor(Math.random() * 1000000000)}`;
}

export function createDirectory(name, ancestorId = null) {
  return postJson('/v1/files/mkdir', {
    name,
    ancestor_id: ancestorId,
    purpose: 'assistants',
    description: 'benchmark fixture',
  }, 'POST /v1/files/mkdir');
}

export function createDataset(name, type = 'qa') {
  return postJson('/v1/datasets/create', {
    name,
    type,
    remark: 'benchmark fixture',
  }, 'POST /v1/datasets/create');
}

export function createQa(datasetId, suffix) {
  return postJson('/v1/datasets/qa/create', {
    dataset_id: datasetId,
    question: `benchmark question ${suffix}`,
    answer: `benchmark answer ${suffix}`,
    reasoning: 'benchmark reasoning',
    scoring_criteria: 'exact',
  }, 'POST /v1/datasets/qa/create');
}
