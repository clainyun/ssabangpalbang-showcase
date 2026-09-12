import assert from 'node:assert/strict';
import { once } from 'node:events';
import { test } from 'node:test';

import { createMediaGateway } from '../src/index.mjs';

const TOKEN = 'test-internal-token-never-log-this';
const UPLOAD_KEY =
  'pending/stt-audio/2026/08/22/00000000-0000-4000-8000-000000000000.m4a';
const FINAL_KEY =
  'stt-audio/2026/08/22/00000000-0000-4000-8000-000000000000.m4a';

test('설정 token이 없으면 fail closed 한다', () => {
  assert.throws(
    () =>
      createMediaGateway({
        internalToken: '',
        storage: new FakeStorage(),
      }),
    /MEDIA_GATEWAY_INTERNAL_TOKEN/u,
  );
});

test('Authorization이 없으면 401이고 Storage를 호출하지 않는다', async (t) => {
  const storage = new FakeStorage();
  const server = await startServer(t, storage);

  const response = await call(server, '/media/upload-url', {
    fileUsage: 'STT_AUDIO',
    contentType: 'audio/mp4',
    sizeBytes: 1234,
  });

  assert.equal(response.status, 401);
  assert.equal(storage.calls.length, 0);
});

test('인증 실패는 잘못된 JSON 본문 처리보다 먼저 종료한다', async (t) => {
  const storage = new FakeStorage();
  const server = await startServer(t, storage);
  const address = server.address();

  const response = await fetch(
    `http://127.0.0.1:${address.port}/media/upload-url`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{invalid-json',
    },
  );

  assert.equal(response.status, 401);
  assert.equal((await response.json()).code, 'MEDIA_GATEWAY_UNAUTHORIZED');
  assert.equal(storage.calls.length, 0);
});

test('잘못된 Bearer token이면 401이고 Storage를 호출하지 않는다', async (t) => {
  const storage = new FakeStorage();
  const server = await startServer(t, storage);

  const response = await call(
    server,
    '/media/upload-url',
    {
      fileUsage: 'STT_AUDIO',
      contentType: 'audio/mp4',
      sizeBytes: 1234,
    },
    'wrong-token',
  );

  assert.equal(response.status, 401);
  assert.equal(storage.calls.length, 0);
});

test('정상 token이면 upload-url schema와 signed Content-Type을 반환한다', async (t) => {
  const storage = new FakeStorage();
  const server = await startServer(t, storage);

  const response = await call(
    server,
    '/media/upload-url',
    {
      fileUsage: 'FIELD_PHOTO',
      contentType: 'image/jpeg',
      sizeBytes: 1234,
    },
    TOKEN,
  );

  assert.equal(response.status, 200);
  assert.equal(response.body.fileUsage, 'FIELD_PHOTO');
  assert.match(response.body.uploadKey, /^pending\/field-photo\//u);
  assert.equal(response.body.uploadUrl, 'https://storage.example/upload');
  assert.equal(response.body.method, 'PUT');
  assert.deepEqual(response.body.requiredHeaders, {
    'Content-Type': 'image/jpeg',
  });
  assert.equal(response.body.expectedSizeBytes, 1234);
  assert.ok(Date.parse(response.body.expiresAt) > Date.now());
  assert.deepEqual(storage.calls[0].slice(0, 2), [
    'createUploadUrl',
    response.body.uploadKey,
  ]);
  assert.equal(storage.calls[0][2], 'image/jpeg');
});

test('verify 대상이 없으면 404를 반환한다', async (t) => {
  const storage = new FakeStorage();
  const server = await startServer(t, storage);

  const response = await verify(server);

  assert.equal(response.status, 404);
  assert.equal(response.body.code, 'MEDIA_OBJECT_NOT_FOUND');
});

test('verify size mismatch는 Spring 호환 code를 반환한다', async (t) => {
  const storage = new FakeStorage({
    [UPLOAD_KEY]: metadata('audio/mp4', 1200),
  });
  const server = await startServer(t, storage);

  const response = await verify(server);

  assert.equal(response.status, 422);
  assert.equal(response.body.code, 'MEDIA_SIZE_MISMATCH');
  assert.equal(response.body.details.expectedSizeBytes, 1234);
  assert.equal(response.body.details.actualSizeBytes, 1200);
  assert.equal(storage.objects.has(UPLOAD_KEY), false);
});

test('verify Content-Type mismatch는 Spring 호환 code를 반환한다', async (t) => {
  const storage = new FakeStorage({
    [UPLOAD_KEY]: metadata('audio/mpeg', 1234),
  });
  const server = await startServer(t, storage);

  const response = await verify(server);

  assert.equal(response.status, 422);
  assert.equal(response.body.code, 'MEDIA_CONTENT_TYPE_INVALID');
  assert.equal(response.body.details.expectedContentType, 'audio/mp4');
  assert.equal(response.body.details.actualContentType, 'audio/mpeg');
  assert.equal(storage.objects.has(UPLOAD_KEY), false);
});

test('verify 성공 시 pending 객체를 final key로 승격한다', async (t) => {
  const storage = new FakeStorage({
    [UPLOAD_KEY]: metadata('audio/mp4', 1234),
  });
  const server = await startServer(t, storage);

  const response = await verify(server);

  assert.equal(response.status, 200);
  assert.deepEqual(response.body, {
    verified: true,
    fileUsage: 'STT_AUDIO',
    s3Key: FINAL_KEY,
    contentType: 'audio/mp4',
    sizeBytes: 1234,
    etag: 'test-etag',
  });
  assert.equal(storage.objects.has(UPLOAD_KEY), false);
  assert.equal(storage.objects.has(FINAL_KEY), true);
});

test('verify 재호출은 이미 승격된 동일 final key를 멱등 반환한다', async (t) => {
  const storage = new FakeStorage({
    [FINAL_KEY]: metadata('audio/mp4', 1234),
  });
  const server = await startServer(t, storage);

  const response = await verify(server);

  assert.equal(response.status, 200);
  assert.equal(response.body.s3Key, FINAL_KEY);
  assert.equal(
    storage.calls.some(([operation]) => operation === 'promote'),
    false,
  );
});

test('download-url은 존재 확인 후 HTTPS signed GET URL을 반환한다', async (t) => {
  const storage = new FakeStorage({
    [FINAL_KEY]: metadata('audio/mp4', 1234),
  });
  const server = await startServer(t, storage);

  const response = await call(
    server,
    '/media/download-url',
    { fileUsage: 'STT_AUDIO', s3Key: FINAL_KEY },
    TOKEN,
  );

  assert.equal(response.status, 200);
  assert.equal(response.body.fileUsage, 'STT_AUDIO');
  assert.equal(response.body.s3Key, FINAL_KEY);
  assert.equal(response.body.downloadUrl, 'https://storage.example/download');
  assert.ok(Date.parse(response.body.expiresAt) > Date.now());
});

test('delete는 존재하는 객체와 이미 없는 객체 모두 멱등 성공한다', async (t) => {
  const storage = new FakeStorage({
    [FINAL_KEY]: metadata('audio/mp4', 1234),
  });
  const server = await startServer(t, storage);

  const first = await call(
    server,
    '/media/delete',
    { fileUsage: 'STT_AUDIO', s3Key: FINAL_KEY },
    TOKEN,
  );
  const second = await call(
    server,
    '/media/delete',
    { fileUsage: 'STT_AUDIO', s3Key: FINAL_KEY },
    TOKEN,
  );

  assert.equal(first.status, 200);
  assert.equal(second.status, 200);
  assert.deepEqual(second.body, {
    deleted: true,
    fileUsage: 'STT_AUDIO',
    s3Key: FINAL_KEY,
  });
});

test('token은 응답과 로그에 노출되지 않는다', async (t) => {
  const lines = [];
  const storage = new FakeStorage();
  const server = await startServer(t, storage, (event, fields) => {
    lines.push(JSON.stringify({ event, ...fields }));
  });

  const response = await call(
    server,
    '/media/upload-url',
    {
      fileUsage: 'STT_AUDIO',
      contentType: 'audio/mp4',
      sizeBytes: 1234,
    },
    'invalid-token-value',
  );

  const combined = `${JSON.stringify(response.body)}\n${lines.join('\n')}`;
  assert.equal(response.status, 401);
  assert.equal(combined.includes(TOKEN), false);
  assert.equal(combined.includes('invalid-token-value'), false);
});

async function verify(server) {
  return call(
    server,
    '/media/upload/verify',
    {
      fileUsage: 'STT_AUDIO',
      uploadKey: UPLOAD_KEY,
      s3Key: UPLOAD_KEY,
      expectedContentType: 'audio/mp4',
      expectedSizeBytes: 1234,
    },
    TOKEN,
  );
}

async function startServer(t, storage, logger = () => {}) {
  const server = createMediaGateway({
    internalToken: TOKEN,
    storage,
    logger,
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => server.close());
  return server;
}

async function call(server, path, body, token = undefined) {
  const address = server.address();
  const headers = { 'Content-Type': 'application/json' };
  if (token !== undefined) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(
    `http://127.0.0.1:${address.port}${path}`,
    {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    },
  );
  return { status: response.status, body: await response.json() };
}

function metadata(contentType, sizeBytes) {
  return {
    contentType,
    sizeBytes,
    etag: 'test-etag',
    generation: '1',
  };
}

class FakeStorage {
  constructor(objects = {}) {
    this.objects = new Map(Object.entries(objects));
    this.calls = [];
  }

  async createUploadUrl(key, contentType, expiresAt) {
    this.calls.push(['createUploadUrl', key, contentType, expiresAt]);
    return 'https://storage.example/upload';
  }

  async createDownloadUrl(key, expiresAt) {
    this.calls.push(['createDownloadUrl', key, expiresAt]);
    return 'https://storage.example/download';
  }

  async getMetadata(key) {
    this.calls.push(['getMetadata', key]);
    return this.objects.get(key) ?? null;
  }

  async promote(uploadKey, finalKey) {
    this.calls.push(['promote', uploadKey, finalKey]);
    if (this.objects.has(finalKey)) {
      return { created: false };
    }
    const source = this.objects.get(uploadKey);
    if (!source) {
      return { sourceChanged: true };
    }
    this.objects.set(finalKey, { ...source });
    return { created: true };
  }

  async delete(key, generation = undefined) {
    this.calls.push(['delete', key, generation]);
    const current = this.objects.get(key);
    if (
      current &&
      generation !== undefined &&
      current.generation !== generation
    ) {
      return { deleted: false, changed: true };
    }
    const missing = !this.objects.delete(key);
    return { deleted: true, missing };
  }
}
