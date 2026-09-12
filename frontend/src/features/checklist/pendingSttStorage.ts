import * as SecureStore from 'expo-secure-store';

const STORAGE_KEY_PREFIX = 'checklist-pending-stt-v2';
const PENDING_STT_MAX_AGE_MS = 24 * 60 * 60 * 1000;

interface PendingSttBase {
  studyId: number;
  checklistItemId: number;
  createdAt: number;
}

export interface PendingSttSubmission extends PendingSttBase {
  kind: 'submission';
  audioFileId: number;
  clientRequestId: string;
}

export interface PendingSttJob extends PendingSttBase {
  kind: 'job';
  sttId: string;
  retryClientRequestId?: string;
}

export type PendingSttOperation = PendingSttSubmission | PendingSttJob;

const operationQueues = new Map<string, Promise<unknown>>();

function storageKey(studyId: number, checklistItemId: number): string {
  return `${STORAGE_KEY_PREFIX}.${studyId}.${checklistItemId}`;
}

function isPendingSttOperation(value: unknown): value is PendingSttOperation {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Partial<PendingSttOperation>;
  const baseIsValid =
    Number.isInteger(candidate.studyId) &&
    Number(candidate.studyId) > 0 &&
    Number.isInteger(candidate.checklistItemId) &&
    Number(candidate.checklistItemId) > 0 &&
    typeof candidate.createdAt === 'number' &&
    Number.isFinite(candidate.createdAt);
  if (!baseIsValid) return false;

  if (candidate.kind === 'submission') {
    const submission = candidate as Partial<PendingSttSubmission>;
    return (
      Number.isInteger(submission.audioFileId) &&
      Number(submission.audioFileId) > 0 &&
      typeof submission.clientRequestId === 'string' &&
      submission.clientRequestId.length > 0
    );
  }
  if (candidate.kind !== 'job') return false;
  const job = candidate as Partial<PendingSttJob>;
  return (
    typeof job.sttId === 'string' &&
    job.sttId.length > 0 &&
    (job.retryClientRequestId === undefined ||
      (typeof job.retryClientRequestId === 'string' && job.retryClientRequestId.length > 0))
  );
}

function isActiveOperation(value: unknown, now: number): value is PendingSttOperation {
  return (
    isPendingSttOperation(value) &&
    value.createdAt <= now &&
    now - value.createdAt < PENDING_STT_MAX_AGE_MS
  );
}

function queueOperation<T>(key: string, operation: () => Promise<T>): Promise<T> {
  const previous = operationQueues.get(key) ?? Promise.resolve();
  const next = previous.then(operation, operation);
  const tracked = next.finally(() => {
    if (operationQueues.get(key) === tracked) operationQueues.delete(key);
  });
  operationQueues.set(key, tracked);
  return tracked;
}

async function readOperation(
  studyId: number,
  checklistItemId: number,
  now = Date.now(),
): Promise<PendingSttOperation | null> {
  const key = storageKey(studyId, checklistItemId);
  return queueOperation(key, async () => {
    const raw = await SecureStore.getItemAsync(key);
    if (!raw) return null;

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      await SecureStore.deleteItemAsync(key);
      return null;
    }

    if (
      !isActiveOperation(parsed, now) ||
      parsed.studyId !== studyId ||
      parsed.checklistItemId !== checklistItemId
    ) {
      await SecureStore.deleteItemAsync(key);
      return null;
    }
    return parsed;
  });
}

function operationIdentity(operation: PendingSttOperation): string {
  return operation.kind === 'job'
    ? `job:${operation.sttId}`
    : `submission:${operation.clientRequestId}`;
}

export function getPendingSttOperation(
  studyId: number,
  checklistItemId: number,
): Promise<PendingSttOperation | null> {
  return readOperation(studyId, checklistItemId);
}

export function savePendingSttSubmission(
  submission: Omit<PendingSttSubmission, 'kind' | 'createdAt'> & { createdAt?: number },
): Promise<void> {
  const key = storageKey(submission.studyId, submission.checklistItemId);
  return queueOperation(key, () =>
    SecureStore.setItemAsync(
      key,
      JSON.stringify({
        ...submission,
        kind: 'submission',
        createdAt: submission.createdAt ?? Date.now(),
      } satisfies PendingSttSubmission),
    ),
  );
}

export function savePendingSttJob(
  job: Omit<PendingSttJob, 'kind' | 'createdAt'> & { createdAt?: number },
): Promise<void> {
  const key = storageKey(job.studyId, job.checklistItemId);
  return queueOperation(key, () =>
    SecureStore.setItemAsync(
      key,
      JSON.stringify({
        ...job,
        kind: 'job',
        createdAt: job.createdAt ?? Date.now(),
      } satisfies PendingSttJob),
    ),
  );
}

export async function removePendingSttOperation(
  studyId: number,
  checklistItemId: number,
  expectedIdentity?: string,
): Promise<void> {
  const key = storageKey(studyId, checklistItemId);
  return queueOperation(key, async () => {
    if (expectedIdentity !== undefined) {
      const raw = await SecureStore.getItemAsync(key);
      if (!raw) return;
      let current: unknown;
      try {
        current = JSON.parse(raw);
      } catch {
        await SecureStore.deleteItemAsync(key);
        return;
      }
      if (!isPendingSttOperation(current) || operationIdentity(current) !== expectedIdentity) {
        return;
      }
    }
    await SecureStore.deleteItemAsync(key);
  });
}
