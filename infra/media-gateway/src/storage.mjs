import { Storage } from '@google-cloud/storage';

export class GcsMediaStorage {
  constructor({ bucketName, projectId = undefined, storage = undefined }) {
    if (typeof bucketName !== 'string' || bucketName.length === 0) {
      throw new Error('MEDIA_GCS_BUCKET is required');
    }
    this.bucket = (storage ?? new Storage({ projectId })).bucket(bucketName);
  }

  async createUploadUrl(objectKey, contentType, expiresAt) {
    const [url] = await this.bucket.file(objectKey).getSignedUrl({
      version: 'v4',
      action: 'write',
      expires: expiresAt,
      contentType,
    });
    return url;
  }

  async createDownloadUrl(objectKey, expiresAt) {
    const [url] = await this.bucket.file(objectKey).getSignedUrl({
      version: 'v4',
      action: 'read',
      expires: expiresAt,
    });
    return url;
  }

  async getMetadata(objectKey) {
    try {
      const [metadata] = await this.bucket.file(objectKey).getMetadata();
      return toObjectMetadata(metadata);
    } catch (error) {
      if (isStatus(error, 404)) {
        return null;
      }
      throw error;
    }
  }

  async promote(uploadKey, finalKey, sourceGeneration) {
    const source = this.bucket.file(uploadKey, {
      generation: sourceGeneration,
    });
    try {
      await source.copy(this.bucket.file(finalKey), {
        preconditionOpts: { ifGenerationMatch: 0 },
      });
      return { created: true };
    } catch (error) {
      if (isStatus(error, 412)) {
        return { created: false };
      }
      if (isStatus(error, 404)) {
        return { sourceChanged: true };
      }
      throw error;
    }
  }

  async delete(objectKey, generation = undefined) {
    const options =
      generation === undefined
        ? {}
        : { ifGenerationMatch: generation };
    try {
      await this.bucket.file(objectKey).delete(options);
      return { deleted: true, missing: false };
    } catch (error) {
      if (isStatus(error, 404)) {
        return { deleted: true, missing: true };
      }
      if (isStatus(error, 412)) {
        return { deleted: false, changed: true };
      }
      throw error;
    }
  }
}

function toObjectMetadata(metadata) {
  const sizeBytes = Number(metadata.size);
  if (!Number.isSafeInteger(sizeBytes) || sizeBytes < 0) {
    throw new Error('GCS returned an invalid object size');
  }
  return {
    contentType: metadata.contentType ?? '',
    sizeBytes,
    etag: metadata.etag ?? '',
    generation: metadata.generation,
  };
}

function isStatus(error, status) {
  return (
    error !== null &&
    typeof error === 'object' &&
    ('code' in error || 'statusCode' in error) &&
    (Number(error.code) === status || Number(error.statusCode) === status)
  );
}
