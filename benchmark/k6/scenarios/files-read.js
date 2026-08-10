import { expectSuccess, get, mustSucceed, postJson, createDirectory, unique } from '../lib/client.js';
import { SPACE_CODE, loadOptions, numberEnv } from '../lib/config.js';
import { seedDirectories } from '../lib/fixtures.js';
import { handleSummary } from '../lib/summary.js';

export const options = loadOptions();

export function setup() {
  const count = numberEnv('SEED_FILES', 40);
  const root = mustSucceed(createDirectory(unique('read-root')), 'create read root', (value) => Boolean(value && value.id));
  const ids = seedDirectories(count, root.id, unique('read-entry'));
  return { rootId: root.id, ids };
}

export default function (fixture) {
  const roll = Math.random();
  const id = fixture.ids[Math.floor(Math.random() * fixture.ids.length)];

  if (roll < 0.4) {
    expectSuccess(get(`/v1/files/${id}`, 'GET /v1/files/{id}'), 'read file metadata', (value) => value && value.id === id);
    return;
  }

  if (roll < 0.7) {
    expectSuccess(postJson('/v1/files/page', {
      ancestor_id: fixture.rootId,
      page: Math.random() < 0.8 ? 1 : Math.max(1, Math.ceil(fixture.ids.length / 20)),
      page_size: 20,
      order: 'desc',
    }, 'POST /v1/files/page'), 'page files', (value) => value && Array.isArray(value.data));
    return;
  }

  if (roll < 0.85) {
    expectSuccess(get(`/v1/files?ancestor_id=${fixture.rootId}&limit=100`, 'GET /v1/files'), 'list files',
      (value) => value && Array.isArray(value.data));
    return;
  }

  const batch = fixture.ids.slice(0, Math.min(100, fixture.ids.length));
  expectSuccess(postJson('/v1/files/ancestor-ids', {
    space_code: SPACE_CODE,
    file_ids: batch,
  }, 'POST /v1/files/ancestor-ids'), 'batch ancestors', (value) => value && typeof value === 'object');
}

export { handleSummary };
