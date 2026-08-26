import { mustSucceed, createDirectory, createDataset, createQa, uploadFile, unique } from './client.js';

export function seedDirectories(count, ancestorId, prefix) {
  const ids = [];
  for (let index = 0; index < count; index += 1) {
    const body = mustSucceed(
      createDirectory(`${prefix}-${String(index).padStart(6, '0')}`, ancestorId),
      'seed directory',
      (value) => Boolean(value && value.id),
    );
    ids.push(body.id);
  }
  return ids;
}

export function seedDirectoryTree(count, rootId, prefix, branchingFactor = 10) {
  const ids = [rootId];
  const parents = [rootId];
  let parentIndex = 0;

  while (ids.length < count) {
    const parentId = parents[parentIndex];
    parentIndex += 1;
    const children = Math.min(branchingFactor, count - ids.length);
    for (let child = 0; child < children; child += 1) {
      const index = ids.length;
      const directory = mustSucceed(
        createDirectory(`${prefix}-${String(index).padStart(6, '0')}`, parentId),
        'seed directory tree',
        (value) => Boolean(value && value.id),
      );
      ids.push(directory.id);
      parents.push(directory.id);
    }
  }
  return ids;
}

export function seedQaDataset(count, prefix) {
  const dataset = mustSucceed(
    createDataset(unique(`${prefix}-dataset`)),
    'seed dataset',
    (value) => Boolean(value && value.dataset_id),
  );
  const itemIds = [];
  for (let index = 0; index < count; index += 1) {
    const qa = mustSucceed(
      createQa(dataset.dataset_id, `${prefix}-${index}`),
      'seed QA',
      (value) => Boolean(value && value.item_id),
    );
    itemIds.push(qa.item_id);
  }
  return { datasetId: dataset.dataset_id, itemIds };
}

export function seedUploadedFile(fileBody, prefix, ancestorId = null) {
  const file = mustSucceed(
    uploadFile(fileBody, `${unique(prefix)}.bin`, ancestorId),
    'seed uploaded file',
    (value) => Boolean(value && value.id),
  );
  return file.id;
}
