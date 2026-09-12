import * as Crypto from 'expo-crypto';
import {
  AudioModule,
  RecordingPresets,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from 'expo-audio';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { appAlert } from '@/components/AppDialog';
import {
  getSttStatus,
  requestStt,
  retryStt,
  SttApiError,
  type SttStatusResult,
} from '@/features/checklist/api/stt';
import {
  getPendingSttOperation,
  removePendingSttOperation,
  savePendingSttJob,
  savePendingSttSubmission,
  type PendingSttSubmission,
} from '@/features/checklist/pendingSttStorage';
import { MediaUploadError, uploadMedia } from '@/features/media/api/mediaUpload';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { useAuthStore } from '@/store/authStore';

const POLL_INTERVAL_MS = 2_500;
const MAX_CONSECUTIVE_POLL_FAILURES = 3;
const MAX_AUDIO_SIZE_BYTES = 50 * 1024 * 1024;
const MAX_RECORDING_DURATION_MS = 10 * 60 * 1000;
const MAX_ACTIVE_POLL_AGE_MS = 30 * 60 * 1000;

export type ChecklistItemSttPhase =
  | 'recovering'
  | 'idle'
  | 'preparing'
  | 'recording'
  | 'uploading'
  | 'pending'
  | 'processing'
  | 'failed';

type RetryMode = 'recover' | 'submit' | 'poll' | 'job' | 'refresh' | null;

interface ControllerState {
  phase: ChecklistItemSttPhase;
  errorMessage: string | null;
  retryable: boolean;
  retryMode: RetryMode;
  hasPendingJob: boolean;
}

interface UseChecklistItemSttOptions {
  studyId: number;
  checklistItemId: number;
  readOnly: boolean;
  onCompleted: (result: SttStatusResult) => void | Promise<void>;
}

export interface ChecklistItemSttController {
  phase: ChecklistItemSttPhase;
  durationMillis: number;
  errorMessage: string | null;
  retryable: boolean;
  isBusy: boolean;
  canToggleRecording: boolean;
  canRetry: boolean;
  toggleRecording: () => void;
  retry: () => void;
}

const INITIAL_STATE: ControllerState = {
  phase: 'recovering',
  errorMessage: null,
  retryable: false,
  retryMode: null,
  hasPendingJob: false,
};

const TERMINAL_STATUS_ERROR_CODES = new Set([
  'FIELD_STT_NOT_FOUND',
  'FIELD_STT_STATUS_FORBIDDEN',
  'FIELD_STT_STUDY_MISMATCH',
]);

const TERMINAL_RETRY_ERROR_CODES = new Set([
  'FIELD_STT_AUDIO_EXPIRED',
  'FIELD_STT_NOT_FOUND',
  'FIELD_STT_RETRY_NOT_ALLOWED',
  'FIELD_STT_RETRY_FORBIDDEN',
]);

const TERMINAL_SUBMISSION_ERROR_CODES = new Set([
  'FIELD_STT_AUDIO_INVALID',
  'FIELD_STT_IDEMPOTENCY_KEY_REUSED',
  'FIELD_STT_REQUEST_FORBIDDEN',
  'MEDIA_FILE_NOT_FOUND',
  'CHECKLIST_ITEM_NOT_FOUND',
  'FIELD_PARTICIPANT_ALREADY_ENDED',
  'FIELD_STT_AUDIO_EXPIRED',
]);

export function useChecklistItemStt({
  studyId,
  checklistItemId,
  readOnly,
  onCompleted,
}: UseChecklistItemSttOptions): ChecklistItemSttController {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder);

  const [state, setState] = useState<ControllerState>(INITIAL_STATE);
  const mountedRef = useRef(true);
  const readOnlyRef = useRef(readOnly);
  const appActiveRef = useRef(AppState.currentState === 'active');
  const actionInFlightRef = useRef(false);
  const recordingRef = useRef(false);
  const recordingCancelledRef = useRef(false);
  const stopPromiseRef = useRef<Promise<void> | null>(null);
  const recordingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stopAndSubmitRef = useRef<() => Promise<void>>(async () => {});
  const submissionRef = useRef<PendingSttSubmission | null>(null);
  const sttIdRef = useRef<string | null>(null);
  const pendingJobCreatedAtRef = useRef<number | null>(null);
  const retryClientRequestIdRef = useRef<string | null>(null);
  const completedResultRef = useRef<SttStatusResult | null>(null);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollInFlightRef = useRef(false);
  const pollFailuresRef = useRef(0);
  const recoveryInFlightRef = useRef(false);
  const pollRef = useRef<() => Promise<void>>(async () => {});
  const recoverRef = useRef<() => Promise<void>>(async () => {});
  const resumeSubmissionRef = useRef<() => Promise<void>>(async () => {});
  const onCompletedRef = useRef(onCompleted);

  useEffect(() => {
    readOnlyRef.current = readOnly;
  }, [readOnly]);

  useEffect(() => {
    onCompletedRef.current = onCompleted;
  }, [onCompleted]);

  const clearPollTimer = useCallback(() => {
    if (pollTimerRef.current !== null) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }, []);

  const clearRecordingTimeout = useCallback(() => {
    if (recordingTimeoutRef.current !== null) {
      clearTimeout(recordingTimeoutRef.current);
      recordingTimeoutRef.current = null;
    }
  }, []);

  const schedulePoll = useCallback((delayMs = POLL_INTERVAL_MS) => {
    if (
      !mountedRef.current ||
      !appActiveRef.current ||
      readOnlyRef.current ||
      sttIdRef.current === null ||
      pollTimerRef.current !== null
    ) {
      return;
    }
    pollTimerRef.current = setTimeout(() => {
      pollTimerRef.current = null;
      void pollRef.current();
    }, delayMs);
  }, []);

  const removeCurrentPending = useCallback(
    async (expectedIdentity: string) => {
      try {
        await removePendingSttOperation(studyId, checklistItemId, expectedIdentity);
      } catch {
        // 서버 상태가 정본입니다. SecureStore 정리는 다음 복구 시 다시 시도합니다.
      }
    },
    [checklistItemId, studyId],
  );

  const completeJob = useCallback(
    async (result: SttStatusResult) => {
      clearPollTimer();
      completedResultRef.current = result;
      if (!mountedRef.current) return;

      setState({
        phase: 'processing',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: true,
      });
      try {
        await onCompletedRef.current(result);
      } catch {
        if (!mountedRef.current) return;
        setState({
          phase: 'failed',
          errorMessage: '변환은 완료됐지만 저장된 메모를 새로 불러오지 못했습니다.',
          retryable: true,
          retryMode: 'refresh',
          hasPendingJob: true,
        });
        return;
      }

      await removeCurrentPending(`job:${result.sttId}`);
      if (!mountedRef.current) return;
      if (sttIdRef.current === result.sttId) sttIdRef.current = null;
      completedResultRef.current = null;
      pendingJobCreatedAtRef.current = null;
      retryClientRequestIdRef.current = null;
      setState({
        phase: 'idle',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: false,
      });
    },
    [clearPollTimer, removeCurrentPending],
  );

  const discardPendingJob = useCallback(
    async (sttId: string, message: string) => {
      clearPollTimer();
      if (sttIdRef.current === sttId) sttIdRef.current = null;
      pendingJobCreatedAtRef.current = null;
      retryClientRequestIdRef.current = null;
      completedResultRef.current = null;
      await removeCurrentPending(`job:${sttId}`);
      if (!mountedRef.current) return;
      setState({
        phase: 'failed',
        errorMessage: message,
        retryable: false,
        retryMode: null,
        hasPendingJob: false,
      });
    },
    [clearPollTimer, removeCurrentPending],
  );

  const poll = useCallback(async () => {
    const sttId = sttIdRef.current;
    if (sttId === null || !mountedRef.current || !appActiveRef.current || pollInFlightRef.current) {
      return;
    }

    pollInFlightRef.current = true;
    try {
      const result = await getSttStatus(studyId, sttId, sessionVersion);
      if (!mountedRef.current || sttIdRef.current !== sttId) return;
      if (
        result.sttId !== sttId ||
        result.studyId !== studyId ||
        result.checklistItemId !== checklistItemId
      ) {
        throw new SttApiError(
          'INVALID_RESPONSE',
          '음성 변환 상태 응답이 요청과 일치하지 않습니다.',
          502,
        );
      }
      pollFailuresRef.current = 0;

      if (retryClientRequestIdRef.current !== null) {
        const completedRetryRequestId = retryClientRequestIdRef.current;
        try {
          await savePendingSttJob({
            studyId,
            checklistItemId,
            sttId,
            createdAt: pendingJobCreatedAtRef.current ?? Date.now(),
          });
          if (retryClientRequestIdRef.current === completedRetryRequestId) {
            retryClientRequestIdRef.current = null;
          }
        } catch {
          // 다음 정상 상태 조회에서 다시 정리합니다.
        }
      }

      if (result.status === 'DONE') {
        await completeJob(result);
        return;
      }
      if (result.status === 'FAILED') {
        clearPollTimer();
        if (!result.retryable) {
          await discardPendingJob(
            sttId,
            result.failReason ?? '음성을 변환하지 못했습니다. 다시 녹음해 주세요.',
          );
          return;
        }
        setState({
          phase: 'failed',
          errorMessage: result.failReason ?? '음성을 변환하지 못했습니다.',
          retryable: true,
          retryMode: 'job',
          hasPendingJob: true,
        });
        return;
      }

      setState({
        phase: result.status === 'PROCESSING' ? 'processing' : 'pending',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: true,
      });
      const activeSince = pendingJobCreatedAtRef.current ?? Date.parse(result.requestedAt);
      if (!Number.isFinite(activeSince) || Date.now() - activeSince > MAX_ACTIVE_POLL_AGE_MS) {
        clearPollTimer();
        setState({
          phase: 'failed',
          errorMessage: '음성 변환이 오래 걸리고 있습니다. 상태 확인을 다시 시도해 주세요.',
          retryable: true,
          retryMode: 'poll',
          hasPendingJob: true,
        });
        return;
      }
      schedulePoll();
    } catch (error) {
      if (!mountedRef.current || sttIdRef.current !== sttId) return;
      if (
        error instanceof SttApiError &&
        (TERMINAL_STATUS_ERROR_CODES.has(error.code) || isNonRetryableClientError(error))
      ) {
        await discardPendingJob(sttId, error.message);
        return;
      }

      pollFailuresRef.current += 1;
      const message =
        error instanceof SttApiError ? error.message : '음성 변환 상태를 확인하지 못했습니다.';
      if (pollFailuresRef.current >= MAX_CONSECUTIVE_POLL_FAILURES) {
        clearPollTimer();
        setState({
          phase: 'failed',
          errorMessage: `${message} 상태 확인을 다시 시도해 주세요.`,
          retryable: true,
          retryMode: 'poll',
          hasPendingJob: true,
        });
      } else {
        setState((current) => ({ ...current, errorMessage: message }));
        schedulePoll(POLL_INTERVAL_MS * pollFailuresRef.current);
      }
    } finally {
      pollInFlightRef.current = false;
    }
  }, [
    checklistItemId,
    clearPollTimer,
    completeJob,
    discardPendingJob,
    schedulePoll,
    sessionVersion,
    studyId,
  ]);

  useEffect(() => {
    pollRef.current = poll;
  }, [poll]);

  const startPolling = useCallback(() => {
    pollFailuresRef.current = 0;
    clearPollTimer();
    if (appActiveRef.current) schedulePoll(0);
  }, [clearPollTimer, schedulePoll]);

  const recoverPending = useCallback(async () => {
    if (readOnlyRef.current) {
      if (mountedRef.current) setState({ ...INITIAL_STATE, phase: 'idle' });
      return;
    }
    if (recoveryInFlightRef.current || sttIdRef.current !== null) return;
    recoveryInFlightRef.current = true;
    if (mountedRef.current) {
      setState({
        phase: 'recovering',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: false,
      });
    }

    try {
      const pending = await getPendingSttOperation(studyId, checklistItemId);
      if (!mountedRef.current) return;
      if (!pending) {
        setState({
          phase: 'idle',
          errorMessage: null,
          retryable: false,
          retryMode: null,
          hasPendingJob: false,
        });
        return;
      }

      if (pending.kind === 'submission') {
        submissionRef.current = pending;
        setState({
          phase: 'pending',
          errorMessage: null,
          retryable: false,
          retryMode: null,
          hasPendingJob: true,
        });
        void resumeSubmissionRef.current();
        return;
      }

      sttIdRef.current = pending.sttId;
      pendingJobCreatedAtRef.current = pending.createdAt;
      retryClientRequestIdRef.current = pending.retryClientRequestId ?? null;
      setState({
        phase: 'pending',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: true,
      });
      startPolling();
    } catch {
      if (!mountedRef.current) return;
      setState({
        phase: 'failed',
        errorMessage: '진행 중인 음성 변환 정보를 불러오지 못했습니다.',
        retryable: true,
        retryMode: 'recover',
        hasPendingJob: true,
      });
    } finally {
      recoveryInFlightRef.current = false;
    }
  }, [checklistItemId, startPolling, studyId]);

  useEffect(() => {
    recoverRef.current = recoverPending;
  }, [recoverPending]);

  const stopRecorder = useCallback((): Promise<void> => {
    if (stopPromiseRef.current !== null) return stopPromiseRef.current;
    const pendingStop = recorder.stop();
    stopPromiseRef.current = pendingStop;
    const clearPendingStop = () => {
      if (stopPromiseRef.current === pendingStop) stopPromiseRef.current = null;
    };
    void pendingStop.then(clearPendingStop, clearPendingStop);
    return pendingStop;
  }, [recorder]);

  const cancelRecording = useCallback(
    async (message: string, updateUi: boolean) => {
      if (!recordingRef.current) return;
      recordingCancelledRef.current = true;
      clearRecordingTimeout();
      try {
        await stopRecorder();
      } catch {
        // 중단 중 네이티브 레코더가 먼저 닫혔어도 업로드하지 않으면 됩니다.
      } finally {
        recordingRef.current = false;
      }
      try {
        await setAudioModeAsync({ allowsRecording: false });
      } catch {
        // 화면 정리를 막을 이유가 없는 전역 오디오 모드 복원 실패입니다.
      }
      if (updateUi && mountedRef.current) {
        setState({
          phase: 'idle',
          errorMessage: message,
          retryable: false,
          retryMode: null,
          hasPendingJob: false,
        });
      }
    },
    [clearRecordingTimeout, stopRecorder],
  );

  const startRecording = useCallback(async () => {
    if (
      readOnlyRef.current ||
      actionInFlightRef.current ||
      recordingRef.current ||
      sttIdRef.current !== null
    ) {
      return;
    }

    actionInFlightRef.current = true;
    setState({
      phase: 'preparing',
      errorMessage: null,
      retryable: false,
      retryMode: null,
      hasPendingJob: false,
    });
    try {
      let permission = await AudioModule.getRecordingPermissionsAsync();
      if (!permission.granted) {
        const shouldRequest = await explainBeforeRequest('microphone');
        if (!shouldRequest) {
          if (mountedRef.current) setState({ ...INITIAL_STATE, phase: 'idle' });
          return;
        }
        permission = await AudioModule.requestRecordingPermissionsAsync();
      }

      if (!permission.granted) {
        if (permission.canAskAgain) {
          appAlert('마이크 권한 필요', '음성 메모를 녹음하려면 마이크 권한을 허용해 주세요.');
        } else {
          showPermanentlyDeniedAlert('microphone');
        }
        if (mountedRef.current) setState({ ...INITIAL_STATE, phase: 'idle' });
        return;
      }

      if (!mountedRef.current || !appActiveRef.current || readOnlyRef.current) return;
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      if (!mountedRef.current || !appActiveRef.current || readOnlyRef.current) {
        await setAudioModeAsync({ allowsRecording: false });
        return;
      }
      recorder.record();
      recordingCancelledRef.current = false;
      recordingRef.current = true;
      clearRecordingTimeout();
      recordingTimeoutRef.current = setTimeout(() => {
        recordingTimeoutRef.current = null;
        void stopAndSubmitRef.current();
      }, MAX_RECORDING_DURATION_MS);
      setState({
        phase: 'recording',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: false,
      });
    } catch {
      try {
        await setAudioModeAsync({ allowsRecording: false });
      } catch {
        // 아래 사용자 안내가 우선입니다.
      }
      if (mountedRef.current) {
        setState({
          phase: 'idle',
          errorMessage: '녹음을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.',
          retryable: false,
          retryMode: null,
          hasPendingJob: false,
        });
      }
    } finally {
      actionInFlightRef.current = false;
    }
  }, [clearRecordingTimeout, recorder]);

  const sendSubmission = useCallback(
    async (submission: PendingSttSubmission) => {
      try {
        const result = await requestStt(
          studyId,
          submission.audioFileId,
          checklistItemId,
          submission.clientRequestId,
          sessionVersion,
        );
        if (
          result.studyId !== studyId ||
          result.audioFileId !== submission.audioFileId ||
          result.checklistItemId !== checklistItemId
        ) {
          throw new SttApiError(
            'INVALID_RESPONSE',
            '음성 변환 요청 응답이 요청과 일치하지 않습니다.',
            502,
          );
        }

        sttIdRef.current = result.sttId;
        pendingJobCreatedAtRef.current = submission.createdAt;
        submissionRef.current = null;
        let storageWarning: string | null = null;
        try {
          await savePendingSttJob({
            studyId,
            checklistItemId,
            sttId: result.sttId,
            createdAt: submission.createdAt,
          });
        } catch {
          storageWarning =
            '진행 상태를 기기에 저장하지 못했습니다. 완료될 때까지 화면을 유지해 주세요.';
        }

        if (mountedRef.current) {
          setState({
            phase: result.status === 'PROCESSING' ? 'processing' : 'pending',
            errorMessage: storageWarning,
            retryable: false,
            retryMode: null,
            hasPendingJob: true,
          });
        }
        startPolling();
      } catch (error) {
        if (!mountedRef.current) return;
        if (
          error instanceof SttApiError &&
          (TERMINAL_SUBMISSION_ERROR_CODES.has(error.code) || isNonRetryableClientError(error))
        ) {
          submissionRef.current = null;
          await removeCurrentPending(`submission:${submission.clientRequestId}`);
          if (!mountedRef.current) return;
          setState({
            phase: 'failed',
            errorMessage: error.message,
            retryable: false,
            retryMode: null,
            hasPendingJob: false,
          });
          return;
        }
        setState({
          phase: 'failed',
          errorMessage:
            error instanceof SttApiError
              ? `${error.message} 같은 요청으로 다시 시도할 수 있습니다.`
              : '음성 변환 요청을 완료하지 못했습니다. 같은 요청으로 다시 시도해 주세요.',
          retryable: true,
          retryMode: 'submit',
          hasPendingJob: true,
        });
      }
    },
    [checklistItemId, removeCurrentPending, sessionVersion, startPolling, studyId],
  );

  const resumeSubmission = useCallback(async () => {
    const submission = submissionRef.current;
    if (readOnlyRef.current || submission === null || actionInFlightRef.current) return;
    actionInFlightRef.current = true;
    if (mountedRef.current) {
      setState({
        phase: 'pending',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: true,
      });
    }
    try {
      await sendSubmission(submission);
    } finally {
      actionInFlightRef.current = false;
    }
  }, [sendSubmission]);

  useEffect(() => {
    resumeSubmissionRef.current = resumeSubmission;
  }, [resumeSubmission]);

  const stopAndSubmit = useCallback(async () => {
    if (!recordingRef.current || actionInFlightRef.current) return;
    actionInFlightRef.current = true;
    clearRecordingTimeout();
    setState({
      phase: 'uploading',
      errorMessage: null,
      retryable: false,
      retryMode: null,
      hasPendingJob: false,
    });

    try {
      await stopRecorder();
      recordingRef.current = false;
      await setAudioModeAsync({ allowsRecording: false });
      if (recordingCancelledRef.current || !appActiveRef.current || readOnlyRef.current) return;
      const uri = recorder.uri;
      if (!uri) throw new Error('RECORDED_AUDIO_MISSING');

      const audio = await inspectRecordedAudio(uri);
      if (audio.sizeBytes === 0) throw new Error('RECORDED_AUDIO_EMPTY');
      if (audio.sizeBytes > MAX_AUDIO_SIZE_BYTES) throw new Error('RECORDED_AUDIO_TOO_LARGE');
      if (!accessToken) throw new Error('AUTH_REQUIRED');
      const clientRequestId = Crypto.randomUUID();

      const uploaded = await uploadMedia(
        accessToken,
        {
          fileUsage: 'STT_AUDIO',
          originalName: `voice-${Date.now()}.${audio.extension}`,
          contentType: audio.contentType,
          sizeBytes: audio.sizeBytes,
          studyId,
          localUri: uri,
        },
        sessionVersion,
      );
      const submission: PendingSttSubmission = {
        kind: 'submission',
        studyId,
        checklistItemId,
        audioFileId: uploaded.fileId,
        clientRequestId,
        createdAt: Date.now(),
      };
      try {
        await savePendingSttSubmission(submission);
      } catch {
        throw new Error('PENDING_STT_SAVE_FAILED');
      }
      submissionRef.current = submission;
      await sendSubmission(submission);
    } catch (error) {
      recordingRef.current = false;
      try {
        await setAudioModeAsync({ allowsRecording: false });
      } catch {
        // 아래 사용자 안내가 우선입니다.
      }
      if (!mountedRef.current) return;
      setState({
        phase: 'idle',
        errorMessage: recordingSubmissionMessage(error),
        retryable: false,
        retryMode: null,
        hasPendingJob: false,
      });
    } finally {
      actionInFlightRef.current = false;
    }
  }, [
    accessToken,
    checklistItemId,
    clearRecordingTimeout,
    recorder,
    sendSubmission,
    sessionVersion,
    stopRecorder,
    studyId,
  ]);

  useEffect(() => {
    stopAndSubmitRef.current = stopAndSubmit;
  }, [stopAndSubmit]);

  const toggleRecording = useCallback(() => {
    if (recordingRef.current) {
      void stopAndSubmit();
      return;
    }
    void startRecording();
  }, [startRecording, stopAndSubmit]);

  const retry = useCallback(() => {
    if (readOnlyRef.current || actionInFlightRef.current) return;
    if (state.retryMode === 'recover') {
      void recoverRef.current();
      return;
    }
    if (state.retryMode === 'submit') {
      void resumeSubmissionRef.current();
      return;
    }
    if (state.retryMode === 'refresh') {
      const completedResult = completedResultRef.current;
      if (completedResult === null) {
        void recoverRef.current();
        return;
      }
      actionInFlightRef.current = true;
      void completeJob(completedResult).finally(() => {
        actionInFlightRef.current = false;
      });
      return;
    }

    const sttId = sttIdRef.current;
    if (sttId === null) return;
    if (state.retryMode === 'poll') {
      setState({
        phase: 'pending',
        errorMessage: null,
        retryable: false,
        retryMode: null,
        hasPendingJob: true,
      });
      startPolling();
      return;
    }
    if (state.retryMode !== 'job') return;

    actionInFlightRef.current = true;
    setState({
      phase: 'pending',
      errorMessage: null,
      retryable: false,
      retryMode: null,
      hasPendingJob: true,
    });
    void (async () => {
      try {
        let retryClientRequestId = retryClientRequestIdRef.current;
        if (retryClientRequestId === null) {
          retryClientRequestId = Crypto.randomUUID();
          retryClientRequestIdRef.current = retryClientRequestId;
          await savePendingSttJob({
            studyId,
            checklistItemId,
            sttId,
            retryClientRequestId,
            createdAt: pendingJobCreatedAtRef.current ?? Date.now(),
          });
        }

        const result = await retryStt(studyId, sttId, retryClientRequestId, sessionVersion);
        if (
          result.sttId !== sttId ||
          result.studyId !== studyId ||
          result.checklistItemId !== checklistItemId
        ) {
          throw new SttApiError(
            'INVALID_RESPONSE',
            '음성 변환 재시도 응답이 요청과 일치하지 않습니다.',
            502,
          );
        }
        if (!mountedRef.current || sttIdRef.current !== sttId) return;

        const retriedAt = Date.now();
        pendingJobCreatedAtRef.current = retriedAt;
        try {
          await savePendingSttJob({
            studyId,
            checklistItemId,
            sttId,
            createdAt: retriedAt,
          });
          if (retryClientRequestIdRef.current === retryClientRequestId) {
            retryClientRequestIdRef.current = null;
          }
        } catch {
          // 상태 조회가 성공하면 동일한 정리를 다시 시도합니다.
        }
        startPolling();
      } catch (error: unknown) {
        if (!mountedRef.current || sttIdRef.current !== sttId) return;
        if (
          error instanceof SttApiError &&
          (TERMINAL_RETRY_ERROR_CODES.has(error.code) || isNonRetryableClientError(error))
        ) {
          await discardPendingJob(sttId, error.message);
          return;
        }
        setState({
          phase: 'failed',
          errorMessage:
            error instanceof SttApiError ? error.message : '음성 변환을 다시 시작하지 못했습니다.',
          retryable: true,
          retryMode: 'job',
          hasPendingJob: true,
        });
      } finally {
        actionInFlightRef.current = false;
      }
    })();
  }, [
    checklistItemId,
    completeJob,
    discardPendingJob,
    sessionVersion,
    startPolling,
    state.retryMode,
    studyId,
  ]);

  useEffect(() => {
    mountedRef.current = true;
    if (!readOnlyRef.current) void recoverRef.current();

    const subscription = AppState.addEventListener('change', (nextState) => {
      appActiveRef.current = nextState === 'active';
      if (nextState !== 'active') {
        clearPollTimer();
        clearRecordingTimeout();
        if (recordingRef.current) {
          void cancelRecording('앱이 백그라운드로 전환되어 녹음을 취소했습니다.', true);
        }
        return;
      }

      if (readOnlyRef.current) return;

      if (submissionRef.current !== null) {
        void resumeSubmissionRef.current();
      } else if (sttIdRef.current !== null) {
        startPolling();
      } else {
        void recoverRef.current();
      }
    });

    return () => {
      mountedRef.current = false;
      appActiveRef.current = false;
      clearPollTimer();
      clearRecordingTimeout();
      subscription.remove();
      if (recordingRef.current) void cancelRecording('', false);
    };
  }, [cancelRecording, clearPollTimer, clearRecordingTimeout, startPolling]);

  useEffect(() => {
    if (readOnly) clearPollTimer();
    if (readOnly && recordingRef.current) {
      void cancelRecording('읽기 전용으로 전환되어 녹음을 취소했습니다.', true);
    }
  }, [cancelRecording, clearPollTimer, readOnly]);

  const isBusy =
    state.phase === 'recovering' ||
    state.phase === 'preparing' ||
    state.phase === 'uploading' ||
    state.phase === 'pending' ||
    state.phase === 'processing';
  const canToggleRecording =
    state.phase === 'recording' ||
    (!readOnly &&
      !state.hasPendingJob &&
      !isBusy &&
      (state.phase === 'idle' || (state.phase === 'failed' && state.retryMode === null)));

  return {
    phase: state.phase,
    durationMillis: state.phase === 'recording' ? recorderState.durationMillis : 0,
    errorMessage: state.errorMessage,
    retryable: state.retryable,
    isBusy,
    canToggleRecording,
    canRetry: state.phase === 'failed' && state.retryMode !== null,
    toggleRecording,
    retry,
  };
}

interface RecordedAudioInfo {
  sizeBytes: number;
  contentType: 'audio/aac' | 'audio/m4a' | 'audio/mp4' | 'audio/mpeg' | 'audio/wav' | 'audio/webm';
  extension: 'aac' | 'm4a' | 'mp4' | 'mp3' | 'wav' | 'webm';
}

async function inspectRecordedAudio(uri: string): Promise<RecordedAudioInfo> {
  const response = await fetch(uri);
  if (!response.ok) throw new Error('RECORDED_AUDIO_READ_FAILED');
  const blob = await response.blob();
  const lowerUri = uri.split('?')[0]?.toLowerCase() ?? '';
  const contentType = blob.type.split(';', 1)[0]?.trim().toLowerCase() ?? '';

  if (contentType === 'audio/webm' || lowerUri.endsWith('.webm')) {
    return { sizeBytes: blob.size, contentType: 'audio/webm', extension: 'webm' };
  }
  if (contentType === 'audio/wav' || contentType === 'audio/x-wav' || lowerUri.endsWith('.wav')) {
    return { sizeBytes: blob.size, contentType: 'audio/wav', extension: 'wav' };
  }
  if (contentType === 'audio/mpeg' || lowerUri.endsWith('.mp3')) {
    return { sizeBytes: blob.size, contentType: 'audio/mpeg', extension: 'mp3' };
  }
  if (contentType === 'audio/m4a' || contentType === 'audio/x-m4a' || lowerUri.endsWith('.m4a')) {
    return { sizeBytes: blob.size, contentType: 'audio/m4a', extension: 'm4a' };
  }
  if (contentType === 'audio/mp4' || lowerUri.endsWith('.mp4')) {
    return { sizeBytes: blob.size, contentType: 'audio/mp4', extension: 'mp4' };
  }
  if (contentType === 'audio/aac' || lowerUri.endsWith('.aac')) {
    return { sizeBytes: blob.size, contentType: 'audio/aac', extension: 'aac' };
  }
  throw new Error('RECORDED_AUDIO_TYPE_UNSUPPORTED');
}

function recordingSubmissionMessage(error: unknown): string {
  if (error instanceof MediaUploadError || error instanceof SttApiError) return error.message;
  if (!(error instanceof Error)) return '음성 메모를 저장하지 못했습니다.';
  if (error.message === 'RECORDED_AUDIO_EMPTY')
    return '녹음된 음성이 없습니다. 다시 녹음해 주세요.';
  if (error.message === 'RECORDED_AUDIO_TOO_LARGE') {
    return '음성 파일이 50MB를 초과했습니다. 더 짧게 녹음해 주세요.';
  }
  if (error.message === 'RECORDED_AUDIO_TYPE_UNSUPPORTED') {
    return '지원하지 않는 음성 형식입니다. 다시 녹음해 주세요.';
  }
  if (error.message === 'PENDING_STT_SAVE_FAILED') {
    return '음성 변환 요청을 안전하게 저장하지 못했습니다. 잠시 후 다시 녹음해 주세요.';
  }
  if (error.message === 'AUTH_REQUIRED') return '로그인 상태를 확인해 주세요.';
  return '음성 메모를 저장하지 못했습니다. 다시 녹음해 주세요.';
}

function isNonRetryableClientError(error: SttApiError): boolean {
  return error.httpStatus !== undefined && error.httpStatus >= 400 && error.httpStatus < 500;
}
