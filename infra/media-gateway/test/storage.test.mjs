import assert from 'node:assert/strict';
import { test } from 'node:test';

import { GcsMediaStorage } from '../src/storage.mjs';

test('pending 삭제 generation을 Storage v8 DeleteOptions로 직접 전달한다', async () => {
  const calls = [];
  const storage = fakeStorage(calls);
  const mediaStorage = new GcsMediaStorage({
    bucketName: 'private-bucket',
    storage,
  });

  const result = await mediaStorage.delete('pending/stt-audio/file.m4a', '7');

  assert.deepEqual(result, { deleted: true, missing: false });
  assert.deepEqual(calls, [
    ['file', 'pending/stt-audio/file.m4a', undefined],
    ['delete', { ifGenerationMatch: '7' }],
  ]);
});

test('승격은 source generation과 final 미존재 precondition을 함께 사용한다', async () => {
  const calls = [];
  const storage = fakeStorage(calls);
  const mediaStorage = new GcsMediaStorage({
    bucketName: 'private-bucket',
    storage,
  });

  const result = await mediaStorage.promote(
    'pending/stt-audio/file.m4a',
    'stt-audio/file.m4a',
    '9',
  );

  assert.deepEqual(result, { created: true });
  assert.deepEqual(calls, [
    ['file', 'pending/stt-audio/file.m4a', { generation: '9' }],
    ['file', 'stt-audio/file.m4a', undefined],
    ['copy', 'stt-audio/file.m4a', {
      preconditionOpts: { ifGenerationMatch: 0 },
    }],
  ]);
});

function fakeStorage(calls) {
  return {
    bucket(bucketName) {
      assert.equal(bucketName, 'private-bucket');
      return {
        file(name, options = undefined) {
          calls.push(['file', name, options]);
          return {
            name,
            async copy(destination, copyOptions) {
              calls.push(['copy', destination.name, copyOptions]);
            },
            async delete(deleteOptions) {
              calls.push(['delete', deleteOptions]);
            },
          };
        },
      };
    },
  };
}
