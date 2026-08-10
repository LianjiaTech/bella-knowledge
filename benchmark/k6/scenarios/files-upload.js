import { expectSuccess, unique, uploadFile } from '../lib/client.js';
import { loadOptions } from '../lib/config.js';
import { handleSummary } from '../lib/summary.js';

const uploadBody = open('/fixtures/upload.bin', 'b');

export const options = loadOptions({
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    application_errors: ['rate<0.01'],
  },
});

export default function () {
  const filename = `${unique('upload')}.bin`;
  const response = uploadFile(uploadBody, filename, null, 'POST /v1/files [upload]');
  expectSuccess(response, 'upload file', (value) => value && value.id && value.bytes >= 0);
}

export { handleSummary };
