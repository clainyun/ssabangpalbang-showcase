import { randomUUID } from 'node:crypto';

const MEBIBYTE = 1024 * 1024;

const POLICIES = Object.freeze({
  FIELD_PHOTO: {
    prefix: 'field-photo',
    maxBytes: 10 * MEBIBYTE,
    contentTypes: new Set(['image/jpeg', 'image/png', 'image/webp']),
  },
  STT_AUDIO: {
    prefix: 'stt-audio',
    maxBytes: 50 * MEBIBYTE,
    contentTypes: new Set([
      'audio/m4a',
      'audio/mp4',
      'audio/mpeg',
      'audio/wav',
      'audio/x-wav',
      'audio/webm',
      'audio/aac',
      'audio/x-m4a',
    ]),
  },
  CHAT_IMAGE: {
    prefix: 'chat-image',
    maxBytes: 10 * MEBIBYTE,
    contentTypes: new Set(['image/jpeg', 'image/png', 'image/webp']),
  },
  POST_ATTACHMENT: {
    prefix: 'post-attachment',
    maxBytes: 20 * MEBIBYTE,
    contentTypes: new Set([
      'image/jpeg',
      'image/png',
      'image/webp',
      'application/pdf',
    ]),
  },
});

const EXTENSIONS = Object.freeze({
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'application/pdf': 'pdf',
  'audio/m4a': 'm4a',
  'audio/mp4': 'm4a',
  'audio/mpeg': 'mp3',
  'audio/wav': 'wav',
  'audio/x-wav': 'wav',
  'audio/webm': 'webm',
  'audio/aac': 'aac',
  'audio/x-m4a': 'm4a',
});

export class PolicyError extends Error {
  constructor(code, message, details = undefined, status = 400) {
    super(message);
    this.name = 'PolicyError';
    this.code = code;
    this.details = details;
    this.status = status;
  }
}

export function validateUploadRequest(fileUsage, contentType, sizeBytes) {
  const policy = policyFor(fileUsage);
  const normalizedContentType = normalizeContentType(contentType);
  if (!policy.contentTypes.has(normalizedContentType)) {
    throw new PolicyError(
      'MEDIA_CONTENT_TYPE_INVALID',
      '지원하지 않는 파일 형식입니다.',
      { fileUsage, contentType: contentType ?? '' },
    );
  }
  if (
    !Number.isSafeInteger(sizeBytes) ||
    sizeBytes < 1 ||
    sizeBytes > policy.maxBytes
  ) {
    throw new PolicyError(
      'MEDIA_FILE_SIZE_EXCEEDED',
      '파일 크기가 허용 범위를 초과했습니다.',
      { maxBytes: policy.maxBytes },
    );
  }
  return { policy, contentType: normalizedContentType, sizeBytes };
}

export function buildUploadKey(fileUsage, contentType, now = new Date()) {
  const policy = policyFor(fileUsage);
  const normalizedContentType = normalizeContentType(contentType);
  const extension = EXTENSIONS[normalizedContentType];
  if (!extension) {
    throw new PolicyError(
      'MEDIA_CONTENT_TYPE_INVALID',
      '지원하지 않는 파일 형식입니다.',
    );
  }
  const year = String(now.getUTCFullYear()).padStart(4, '0');
  const month = String(now.getUTCMonth() + 1).padStart(2, '0');
  const day = String(now.getUTCDate()).padStart(2, '0');
  return `pending/${policy.prefix}/${year}/${month}/${day}/${randomUUID()}.${extension}`;
}

export function finalKeyForUpload(fileUsage, uploadKey) {
  const policy = policyFor(fileUsage);
  const prefix = `pending/${policy.prefix}/`;
  assertSafeKey(uploadKey);
  if (!uploadKey.startsWith(prefix)) {
    throw invalidObjectKey();
  }
  const finalKey = uploadKey.slice('pending/'.length);
  assertManagedFinalKey(fileUsage, finalKey);
  return finalKey;
}

export function assertManagedFinalKey(fileUsage, objectKey) {
  const policy = policyFor(fileUsage);
  assertSafeKey(objectKey);
  if (!objectKey.startsWith(`${policy.prefix}/`)) {
    throw invalidObjectKey();
  }
  return objectKey;
}

export function policyFor(fileUsage) {
  const policy = POLICIES[fileUsage];
  if (!policy) {
    throw new PolicyError(
      'MEDIA_FILE_USAGE_INVALID',
      '지원하지 않는 파일 사용 목적입니다.',
    );
  }
  return policy;
}

export function normalizeContentType(contentType) {
  return typeof contentType === 'string'
    ? contentType.trim().toLowerCase()
    : '';
}

function assertSafeKey(key) {
  if (
    typeof key !== 'string' ||
    key.length === 0 ||
    key.length > 1024 ||
    key.startsWith('/') ||
    key.endsWith('/') ||
    key.includes('\\') ||
    key.includes('//') ||
    /[\u0000-\u001f\u007f]/u.test(key) ||
    key.split('/').some((segment) => segment === '.' || segment === '..')
  ) {
    throw invalidObjectKey();
  }
}

function invalidObjectKey() {
  return new PolicyError(
    'MEDIA_OBJECT_KEY_INVALID',
    '관리되지 않는 미디어 객체 경로입니다.',
  );
}
