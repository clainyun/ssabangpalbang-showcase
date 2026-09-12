import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';

import { isAuthorized, requireConfiguredToken } from './auth.mjs';
import {
  assertManagedFinalKey,
  buildUploadKey,
  finalKeyForUpload,
  normalizeContentType,
  PolicyError,
  validateUploadRequest,
} from './policy.mjs';
import { GcsMediaStorage } from './storage.mjs';

const MAX_REQUEST_BYTES = 32 * 1024;

export function createMediaGateway({
  internalToken,
  storage,
  uploadTtlMs = 15 * 60 * 1000,
  downloadTtlMs = 5 * 60 * 1000,
  now = () => new Date(),
  logger = defaultLogger,
}) {
  const expectedToken = requireConfiguredToken(internalToken);

  return http.createServer(async (request, response) => {
    const requestId = randomUUID();
    const startedAt = Date.now();
    let status = 500;

    try {
      const url = new URL(request.url ?? '/', 'http://media-gateway');
      if (request.method === 'GET' && url.pathname === '/health') {
        status = 200;
        return sendJson(response, status, { status: 'UP' });
      }

      if (url.pathname.startsWith('/media/')) {
        const authorizationValues =
          request.headersDistinct.authorization ?? [];
        if (
          authorizationValues.length !== 1 ||
          !isAuthorized(authorizationValues[0], expectedToken)
        ) {
          request.resume();
          status = 401;
          return sendError(
            response,
            status,
            'MEDIA_GATEWAY_UNAUTHORIZED',
            '인증이 필요합니다.',
          );
        }
      }

      if (request.method !== 'POST') {
        request.resume();
        status = 405;
        response.setHeader('Allow', 'POST');
        return sendError(
          response,
          status,
          'METHOD_NOT_ALLOWED',
          '지원하지 않는 HTTP 메서드입니다.',
        );
      }

      const body = await readJsonBody(request);
      const result = await route({
        path: url.pathname,
        body,
        storage,
        uploadTtlMs,
        downloadTtlMs,
        now,
        logger,
        requestId,
      });
      status = result.status;
      return sendJson(response, status, result.body);
    } catch (error) {
      if (error instanceof HttpError || error instanceof PolicyError) {
        status = error.status;
        return sendError(
          response,
          status,
          error.code,
          error.message,
          error.details,
        );
      }

      logger('request_failed', {
        requestId,
        errorType: error?.name ?? 'Error',
      });
      status = 503;
      return sendError(
        response,
        status,
        'MEDIA_GATEWAY_UNAVAILABLE',
        '미디어 저장소를 사용할 수 없습니다.',
      );
    } finally {
      logger('request_completed', {
        requestId,
        method: request.method,
        path: safePath(request.url),
        status,
        durationMs: Date.now() - startedAt,
      });
    }
  });
}

async function route({
  path,
  body,
  storage,
  uploadTtlMs,
  downloadTtlMs,
  now,
  logger,
  requestId,
}) {
  switch (path) {
    case '/media/upload-url':
      return uploadUrl(body, storage, uploadTtlMs, now);
    case '/media/upload/verify':
      return verifyUpload(body, storage, logger, requestId);
    case '/media/download-url':
      return downloadUrl(body, storage, downloadTtlMs, now);
    case '/media/delete':
      return deleteObject(body, storage);
    default:
      throw new HttpError(
        404,
        'MEDIA_GATEWAY_PATH_NOT_FOUND',
        '미디어 Gateway 경로를 찾을 수 없습니다.',
      );
  }
}

async function uploadUrl(body, storage, ttlMs, now) {
  const { fileUsage, contentType, sizeBytes } = requireObject(body);
  const validated = validateUploadRequest(fileUsage, contentType, sizeBytes);
  const issuedAt = now();
  const expiresAt = new Date(issuedAt.getTime() + ttlMs);
  const uploadKey = buildUploadKey(
    fileUsage,
    validated.contentType,
    issuedAt,
  );
  const signedUrl = await storage.createUploadUrl(
    uploadKey,
    validated.contentType,
    expiresAt,
  );
  assertHttpsUrl(signedUrl);
  return {
    status: 200,
    body: {
      fileUsage,
      uploadKey,
      uploadUrl: signedUrl,
      method: 'PUT',
      requiredHeaders: { 'Content-Type': validated.contentType },
      expectedSizeBytes: sizeBytes,
      expiresAt: expiresAt.toISOString(),
    },
  };
}

async function verifyUpload(body, storage, logger, requestId) {
  const {
    fileUsage,
    uploadKey,
    s3Key,
    expectedContentType,
    expectedSizeBytes,
  } = requireObject(body);
  if (
    typeof uploadKey !== 'string' ||
    typeof s3Key !== 'string' ||
    uploadKey !== s3Key
  ) {
    throw new HttpError(
      400,
      'MEDIA_OBJECT_KEY_INVALID',
      'uploadKey와 s3Key가 일치해야 합니다.',
    );
  }

  const expected = validateUploadRequest(
    fileUsage,
    expectedContentType,
    expectedSizeBytes,
  );
  const finalKey = finalKeyForUpload(fileUsage, uploadKey);
  let finalMetadata = await storage.getMetadata(finalKey);
  let pendingGeneration;

  if (finalMetadata === null) {
    const pendingMetadata = await storage.getMetadata(uploadKey);
    if (pendingMetadata === null) {
      throw objectNotFound();
    }
    pendingGeneration = pendingMetadata.generation;
    await assertPendingMetadata(
      storage,
      uploadKey,
      pendingMetadata,
      expected.contentType,
      expectedSizeBytes,
    );

    const promotion = await storage.promote(
      uploadKey,
      finalKey,
      pendingMetadata.generation,
    );
    if (promotion.sourceChanged) {
      throw uploadChanged();
    }

    finalMetadata = await storage.getMetadata(finalKey);
    if (finalMetadata === null) {
      throw new Error('Promoted GCS object was not found');
    }
    if (!promotion.created) {
      assertFinalMetadata(
        finalMetadata,
        expected.contentType,
        expectedSizeBytes,
      );
    } else {
      assertMetadata(
        finalMetadata,
        expected.contentType,
        expectedSizeBytes,
      );
    }
  } else {
    assertFinalMetadata(
      finalMetadata,
      expected.contentType,
      expectedSizeBytes,
    );
    const pendingMetadata = await storage.getMetadata(uploadKey);
    pendingGeneration = pendingMetadata?.generation;
  }

  if (pendingGeneration !== undefined) {
    try {
      const cleanup = await storage.delete(uploadKey, pendingGeneration);
      if (cleanup.changed) {
        logger('pending_cleanup_skipped', { requestId });
      }
    } catch (error) {
      logger('pending_cleanup_failed', {
        requestId,
        errorType: error?.name ?? 'Error',
      });
    }
  }

  return {
    status: 200,
    body: {
      verified: true,
      fileUsage,
      s3Key: finalKey,
      contentType: normalizeContentType(finalMetadata.contentType),
      sizeBytes: finalMetadata.sizeBytes,
      etag: finalMetadata.etag,
    },
  };
}

async function downloadUrl(body, storage, ttlMs, now) {
  const { fileUsage, s3Key } = requireObject(body);
  assertManagedFinalKey(fileUsage, s3Key);
  if ((await storage.getMetadata(s3Key)) === null) {
    throw objectNotFound();
  }
  const expiresAt = new Date(now().getTime() + ttlMs);
  const signedUrl = await storage.createDownloadUrl(s3Key, expiresAt);
  assertHttpsUrl(signedUrl);
  return {
    status: 200,
    body: {
      fileUsage,
      s3Key,
      downloadUrl: signedUrl,
      expiresAt: expiresAt.toISOString(),
    },
  };
}

async function deleteObject(body, storage) {
  const { fileUsage, s3Key } = requireObject(body);
  assertManagedFinalKey(fileUsage, s3Key);
  await storage.delete(s3Key);
  return {
    status: 200,
    body: { deleted: true, fileUsage, s3Key },
  };
}

async function assertPendingMetadata(
  storage,
  uploadKey,
  metadata,
  expectedContentType,
  expectedSizeBytes,
) {
  try {
    assertMetadata(metadata, expectedContentType, expectedSizeBytes);
  } catch (error) {
    if (
      error instanceof HttpError &&
      (error.code === 'MEDIA_SIZE_MISMATCH' ||
        error.code === 'MEDIA_CONTENT_TYPE_INVALID')
    ) {
      const cleanup = await storage.delete(uploadKey, metadata.generation);
      if (cleanup.changed) {
        throw uploadChanged();
      }
    }
    throw error;
  }
}

function assertMetadata(metadata, expectedContentType, expectedSizeBytes) {
  const actualContentType = normalizeContentType(metadata.contentType);
  if (metadata.sizeBytes !== expectedSizeBytes) {
    throw new HttpError(
      422,
      'MEDIA_SIZE_MISMATCH',
      '업로드한 파일 크기가 요청 정보와 일치하지 않습니다.',
      {
        expectedSizeBytes,
        actualSizeBytes: metadata.sizeBytes,
        expectedContentType,
        actualContentType,
      },
    );
  }
  if (actualContentType !== expectedContentType) {
    throw new HttpError(
      422,
      'MEDIA_CONTENT_TYPE_INVALID',
      '업로드한 파일 형식이 요청 정보와 일치하지 않습니다.',
      {
        expectedContentType,
        actualContentType,
        expectedSizeBytes,
        actualSizeBytes: metadata.sizeBytes,
      },
    );
  }
}

function assertFinalMetadata(
  metadata,
  expectedContentType,
  expectedSizeBytes,
) {
  try {
    assertMetadata(metadata, expectedContentType, expectedSizeBytes);
  } catch (error) {
    if (
      error instanceof HttpError &&
      (error.code === 'MEDIA_SIZE_MISMATCH' ||
        error.code === 'MEDIA_CONTENT_TYPE_INVALID')
    ) {
      throw uploadChanged();
    }
    throw error;
  }
}

function requireObject(body) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw new HttpError(
      400,
      'MEDIA_REQUEST_INVALID',
      '요청 본문이 올바르지 않습니다.',
    );
  }
  return body;
}

async function readJsonBody(request) {
  const declaredLength = Number(request.headers['content-length'] ?? 0);
  if (declaredLength > MAX_REQUEST_BYTES) {
    throw new HttpError(
      413,
      'MEDIA_REQUEST_TOO_LARGE',
      '요청 본문이 너무 큽니다.',
    );
  }

  const chunks = [];
  let received = 0;
  for await (const chunk of request) {
    received += chunk.length;
    if (received > MAX_REQUEST_BYTES) {
      throw new HttpError(
        413,
        'MEDIA_REQUEST_TOO_LARGE',
        '요청 본문이 너무 큽니다.',
      );
    }
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(
      400,
      'MEDIA_REQUEST_INVALID',
      '요청 본문이 올바른 JSON이 아닙니다.',
    );
  }
}

function assertHttpsUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || !url.hostname) {
      throw new Error('not HTTPS');
    }
  } catch {
    throw new Error('Storage returned an unsafe signed URL');
  }
}

function objectNotFound() {
  return new HttpError(
    404,
    'MEDIA_OBJECT_NOT_FOUND',
    '미디어 객체를 찾을 수 없습니다.',
  );
}

function uploadChanged() {
  return new HttpError(
    409,
    'MEDIA_UPLOAD_CHANGED',
    '검증 중 업로드 파일이 변경되었습니다. 다시 업로드해 주세요.',
  );
}

function sendJson(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  response.end(payload);
}

function sendError(response, status, code, message, details = undefined) {
  const body = details === undefined ? { code, message } : { code, message, details };
  return sendJson(response, status, body);
}

function safePath(requestUrl) {
  try {
    return new URL(requestUrl ?? '/', 'http://media-gateway').pathname;
  } catch {
    return '/invalid-url';
  }
}

function defaultLogger(event, fields) {
  process.stdout.write(`${JSON.stringify({ event, ...fields })}\n`);
}

class HttpError extends Error {
  constructor(status, code, message, details = undefined) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function loadConfig(env = process.env) {
  return {
    port: positiveInteger(env.PORT ?? '8080', 'PORT'),
    internalToken: requireConfiguredToken(
      env.MEDIA_GATEWAY_INTERNAL_TOKEN,
    ),
    bucketName: required(env.MEDIA_GCS_BUCKET, 'MEDIA_GCS_BUCKET'),
    projectId: env.GOOGLE_CLOUD_PROJECT || undefined,
    uploadTtlMs:
      positiveInteger(
        env.MEDIA_UPLOAD_URL_TTL_SECONDS ?? '900',
        'MEDIA_UPLOAD_URL_TTL_SECONDS',
      ) * 1000,
    downloadTtlMs:
      positiveInteger(
        env.MEDIA_DOWNLOAD_URL_TTL_SECONDS ?? '300',
        'MEDIA_DOWNLOAD_URL_TTL_SECONDS',
      ) * 1000,
  };
}

function required(value, name) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function positiveInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function start() {
  const config = loadConfig();
  const storage = new GcsMediaStorage(config);
  const server = createMediaGateway({ ...config, storage });
  server.listen(config.port, '0.0.0.0', () => {
    defaultLogger('server_started', { port: config.port });
  });
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  start();
}
