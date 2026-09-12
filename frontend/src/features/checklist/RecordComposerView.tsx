import { BottomSheetTextInput } from '@gorhom/bottom-sheet';
import Ionicons from '@expo/vector-icons/Ionicons';
import * as Crypto from 'expo-crypto';
import { Image } from 'expo-image';
import * as ImageManipulator from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, Text, View } from 'react-native';

import { appAlert } from '@/components/AppDialog';
import { GlossyFill } from '@/components/GlossyFill';
import { TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import { getChecklistExamplePlaceholder } from '@/features/checklist/checklistExamplePlaceholders';
import {
  createPhotoRecord,
  createTextRecord,
  deleteFieldRecord,
  FieldRecordApiError,
  listAllFieldRecords,
  listFieldRecords,
  type FieldRecord,
} from '@/features/checklist/api/fieldRecords';
import { useChecklistItemStt } from '@/features/checklist/useChecklistItemStt';
import { uploadMedia } from '@/features/media/api/mediaUpload';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { useAuthStore } from '@/store/authStore';

interface RecordComposerViewProps {
  studyId: number;
  checklistItemId: number;
  title: string;
  subtitle: string | null;
  readOnly: boolean;
  /** 목록으로 돌아가 상세가 언마운트돼도 유지할 항목별 글 메모 초안. */
  draftText: string;
  onDraftTextChange: (draft: string) => void;
  /** 저장된 원본 초안을 전달해 상위 상태가 현재 값과 비교한 뒤 안전하게 비웁니다. */
  onDraftSaved: (savedDraft: string) => void;
  /**
   * 메모 입력창 포커스 상태를 시트(ChecklistSheet)에 알립니다. 시트는 이 게이트가
   * 올라가 있을 때만 키보드 높이만큼 바닥을 올리므로, 카메라·사진 선택(별도 액티비티)
   * 진입 전에 반드시 false 로 내려 stuck된 키보드 값이 시트를 밀지 못하게 합니다.
   */
  onInputFocusChange: (isFocused: boolean) => void;
  onBack: () => void;
  /** 기록이 하나 늘거나 줄 때 목록 화면의 recordCount 배지를 갱신하기 위해 알립니다. */
  onRecordCountChange: (checklistItemId: number, recordCount: number) => void;
  /** STT 완료 후 페이지 크기와 무관한 서버의 실제 recordCount를 새로 조회합니다. */
  onSttRecordCountRefresh: (checklistItemId: number) => Promise<void>;
}

type RecordsState = 'loading' | 'success' | 'error';

// 홈 화면 "내 스터디 만들기"(진초록 배경) 버튼과 같은 조합 — 그 버튼의 아이콘·글씨 색.
const SAVE_BUTTON_TEXT_COLOR = '#AEFBCF';

export function RecordComposerView({
  studyId,
  checklistItemId,
  title,
  subtitle,
  readOnly,
  draftText,
  onDraftTextChange,
  onDraftSaved,
  onInputFocusChange,
  onBack,
  onRecordCountChange,
  onSttRecordCountRefresh,
}: RecordComposerViewProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);

  const [records, setRecords] = useState<FieldRecord[]>([]);
  const [recordsState, setRecordsState] = useState<RecordsState>('loading');
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const recordsRequestRef = useRef(0);

  const loadRecords = useCallback(
    async (showLoading = true, signal?: AbortSignal) => {
      const requestId = ++recordsRequestRef.current;
      if (showLoading) setRecordsState('loading');
      try {
        const result = readOnly
          ? await listAllFieldRecords(studyId, checklistItemId, signal)
          : await listFieldRecords(studyId, checklistItemId, undefined, signal);
        if (signal?.aborted || requestId !== recordsRequestRef.current) return;
        setRecords(result.content);
        setRecordsState('success');
      } catch {
        if (signal?.aborted || requestId !== recordsRequestRef.current) return;
        setRecordsState('error');
      }
    },
    [checklistItemId, readOnly, studyId],
  );

  const handleSttCompleted = useCallback(async () => {
    const requestId = ++recordsRequestRef.current;
    const [result] = await Promise.all([
      listFieldRecords(studyId, checklistItemId),
      onSttRecordCountRefresh(checklistItemId),
    ]);
    if (requestId !== recordsRequestRef.current) return;
    setRecords(result.content);
    setRecordsState('success');
  }, [checklistItemId, onSttRecordCountRefresh, studyId]);

  const stt = useChecklistItemStt({
    studyId,
    checklistItemId,
    readOnly,
    onCompleted: handleSttCompleted,
  });
  const isSttOperationActive = stt.phase === 'recording' || stt.isBusy || stt.canRetry;
  const sttOperationActiveRef = useRef(isSttOperationActive);
  useEffect(() => {
    sttOperationActiveRef.current = isSttOperationActive;
  }, [isSttOperationActive]);
  // 사진 첨부는 STT가 '녹음 중'(마이크 사용)일 때만 막습니다. 이전에는 복구·업로드·대기·
  // 변환·재시도 전 구간(isSttOperationActive)을 막아, 진행 중인 STT 작업이 끝날 때까지
  // 카메라·사진 선택이 조용히 무시됐습니다(마이크와 실제로 충돌하는 구간은 녹음 중뿐).
  const isSttRecording = stt.phase === 'recording';
  const sttRecordingRef = useRef(isSttRecording);
  useEffect(() => {
    sttRecordingRef.current = isSttRecording;
  }, [isSttRecording]);
  const microphoneDisabled =
    readOnly || (isSaving && stt.phase !== 'recording') || !stt.canToggleRecording;

  useEffect(() => {
    // ChecklistSheet 는 항목을 열 때마다 이 컴포넌트를 새로 마운트합니다(목록 ↔ 기록
    // 화면 전환이 같은 자리에서 다른 컴포넌트로 바뀌는 구조라서요). 그래서 마운트
    // 시점의 recordsState 초기값이 이미 'loading'이라, 여기서 다시 동기로 세팅할
    // 필요가 없습니다(react-hooks/set-state-in-effect).
    const controller = new AbortController();
    void Promise.resolve().then(() => {
      if (!controller.signal.aborted) void loadRecords(false, controller.signal);
    });

    return () => {
      controller.abort();
    };
  }, [loadRecords]);

  const pushRecord = useCallback(
    (record: FieldRecord) => {
      setRecords((current) => {
        const next = [record, ...current];
        onRecordCountChange(checklistItemId, next.length);
        return next;
      });
    },
    [checklistItemId, onRecordCountChange],
  );

  const handleSaveText = useCallback(async () => {
    const trimmed = draftText.trim();
    if (trimmed.length === 0 || readOnly || sttOperationActiveRef.current) return;

    setIsSaving(true);
    setErrorMessage(null);
    try {
      const record = await createTextRecord(studyId, checklistItemId, trimmed, Crypto.randomUUID());
      pushRecord(record);
      onDraftSaved(draftText);
    } catch (error) {
      setErrorMessage(
        error instanceof FieldRecordApiError ? error.message : '메모를 저장하지 못했습니다.',
      );
    } finally {
      setIsSaving(false);
    }
  }, [checklistItemId, draftText, onDraftSaved, pushRecord, readOnly, studyId]);

  const savePhotoAsset = useCallback(
    async (asset: ImagePicker.ImagePickerAsset) => {
      if (sttRecordingRef.current) return;
      if (!accessToken) {
        appAlert('오류', '로그인 상태를 확인해 주세요.');
        return;
      }

      setIsSaving(true);
      setErrorMessage(null);
      try {
        const shouldResize = typeof asset.width === 'number' && asset.width > 1600;
        const processed = await ImageManipulator.manipulateAsync(
          asset.uri,
          shouldResize ? [{ resize: { width: 1600 } }] : [],
          { compress: 0.7, format: ImageManipulator.SaveFormat.JPEG },
        );
        const contentType = 'image/jpeg';
        // 발급 요청의 크기와 실제 S3 업로드 크기가 같아야 하므로 변환 결과를 기준으로
        // 계산합니다. asset.fileSize를 쓰면 변환 전 원본 크기라 완료 검증에 실패합니다.
        const sizeBytes = (await (await fetch(processed.uri)).blob()).size;

        const uploaded = await uploadMedia(
          accessToken,
          {
            fileUsage: 'FIELD_PHOTO',
            contentType,
            sizeBytes,
            studyId,
            localUri: processed.uri,
            originalName: asset.fileName ?? undefined,
          },
          sessionVersion,
        );
        const record = await createPhotoRecord(
          studyId,
          checklistItemId,
          uploaded.fileId,
          Crypto.randomUUID(),
        );
        pushRecord(record);
      } catch (error) {
        console.warn('사진을 저장하지 못했습니다.', error);
        setErrorMessage('사진을 저장하지 못했습니다.');
      } finally {
        setIsSaving(false);
      }
    },
    [accessToken, checklistItemId, pushRecord, sessionVersion, studyId],
  );

  const handleCamera = useCallback(async () => {
    if (readOnly || sttRecordingRef.current) return;

    // 카메라(별도 액티비티)로 넘어가기 전에 키보드를 먼저 닫고 포커스 게이트를 내립니다.
    // 전환 중 close 애니메이션이 유실돼 keyboard.state/height 가 stuck되더라도, 게이트가
    // 내려가 있으면 시트가 키보드 높이만큼 올라간 채 남지 않습니다(create.tsx와 동일 패턴).
    Keyboard.dismiss();
    onInputFocusChange(false);

    const currentPermission = await ImagePicker.getCameraPermissionsAsync();
    let permission = currentPermission;

    if (!currentPermission.granted) {
      const shouldRequest = await explainBeforeRequest('camera');
      if (!shouldRequest) return;
      permission = await ImagePicker.requestCameraPermissionsAsync();
    }

    if (!permission.granted) {
      if (permission.canAskAgain) {
        appAlert('카메라 권한 필요', '사진을 찍으려면 카메라 권한을 허용해 주세요.');
      } else {
        showPermanentlyDeniedAlert('camera');
      }
      return;
    }

    const result = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 1 });
    if (result.canceled || sttRecordingRef.current) return;
    const asset = result.assets[0];
    if (asset) await savePhotoAsset(asset);
  }, [onInputFocusChange, readOnly, savePhotoAsset]);

  const handlePhotoLibrary = useCallback(async () => {
    if (readOnly || sttRecordingRef.current) return;

    // 피커(별도 액티비티)로 넘어가기 전에 키보드를 먼저 닫고 포커스 게이트를 내립니다.
    // (handleCamera 와 동일한 이유 — stuck된 키보드 값이 시트를 밀지 못하게.)
    Keyboard.dismiss();
    onInputFocusChange(false);

    // Android 시스템 포토 피커(expo-image-picker 57)는 별도 권한이 필요 없습니다. 이 앱은
    // READ_MEDIA_IMAGES 를 선언하지 않아 기존 권한 요청이 항상 granted:false 로 막혀
    // 갤러리가 전혀 열리지 않았습니다. 권한 게이트 없이 피커를 바로 엽니다.
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 1,
    });
    if (result.canceled || sttRecordingRef.current) return;
    const asset = result.assets[0];
    if (asset) await savePhotoAsset(asset);
  }, [onInputFocusChange, readOnly, savePhotoAsset]);

  const handleDelete = useCallback(
    async (recordId: number) => {
      if (readOnly || sttOperationActiveRef.current) return;
      try {
        await deleteFieldRecord(studyId, recordId);
        setRecords((current) => {
          const next = current.filter((record) => record.sourceId !== recordId);
          onRecordCountChange(checklistItemId, next.length);
          return next;
        });
      } catch {
        appAlert('오류', '메모를 삭제하지 못했습니다.');
      }
    },
    [checklistItemId, onRecordCountChange, readOnly, studyId],
  );

  return (
    <View style={styles.container}>
      <Pressable
        accessibilityLabel="체크리스트 목록으로 돌아가기"
        accessibilityRole="button"
        hitSlop={8}
        onPress={onBack}
        style={styles.backRow}
      >
        <Ionicons color={MUTED_TEXT_COLOR} name="chevron-back" size={20} />
        <View style={styles.backTextColumn}>
          <Text style={styles.itemTitle}>{title}</Text>
          <Text style={styles.itemSubtitle}>
            {subtitle ||
              (readOnly
                ? '현장에서 남긴 기록을 확인해 보세요'
                : '현장에서 느낀 점을 사진·음성·글로 남겨보세요')}
          </Text>
        </View>
      </Pressable>

      {!readOnly && (
        <View style={styles.captureRow}>
          <Pressable
            accessibilityRole="button"
            disabled={readOnly || isSaving || isSttRecording}
            onPress={handleCamera}
            style={({ pressed }) => [
              styles.captureButton,
              (readOnly || isSaving || isSttRecording) && styles.captureButtonDisabled,
              pressed && styles.pressed,
            ]}
          >
            <GlossyFill base="#A6F0C4" glossOpacity={0.5} light="#E4FFF0" radius={12} />
            <Ionicons color={DARK_GREEN_COLOR} name="camera-outline" size={18} />
            <Text style={styles.captureLabel}>카메라</Text>
          </Pressable>

          <Pressable
            accessibilityRole="button"
            disabled={readOnly || isSaving || isSttRecording}
            onPress={handlePhotoLibrary}
            style={({ pressed }) => [
              styles.captureButton,
              (readOnly || isSaving || isSttRecording) && styles.captureButtonDisabled,
              pressed && styles.pressed,
            ]}
          >
            <GlossyFill base="#A6F0C4" glossOpacity={0.5} light="#E4FFF0" radius={12} />
            <Ionicons color={DARK_GREEN_COLOR} name="images-outline" size={18} />
            <Text style={styles.captureLabel}>사진 선택</Text>
          </Pressable>
        </View>
      )}

      {!readOnly && (
        <View style={styles.textInputShell}>
          <BottomSheetTextInput
            editable={!readOnly}
            multiline
            onBlur={() => onInputFocusChange(false)}
            onChangeText={onDraftTextChange}
            onFocus={() => onInputFocusChange(true)}
            placeholder={getChecklistExamplePlaceholder(title)}
            placeholderTextColor={MUTED_TEXT_COLOR}
            style={styles.textInput}
            value={draftText}
          />
          <Pressable
            accessibilityLabel={
              stt.phase === 'recording' ? '음성 메모 녹음 종료' : '음성 메모 녹음 시작'
            }
            accessibilityRole="button"
            accessibilityState={{
              busy: stt.isBusy,
              disabled: microphoneDisabled,
            }}
            disabled={microphoneDisabled}
            hitSlop={6}
            onPress={stt.toggleRecording}
            style={({ pressed }) => [
              styles.microphoneButton,
              stt.phase === 'recording' && styles.microphoneButtonRecording,
              microphoneDisabled && styles.microphoneButtonDisabled,
              pressed && styles.pressed,
            ]}
          >
            {stt.isBusy ? (
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
            ) : (
              <Ionicons
                color={stt.phase === 'recording' ? '#FFFFFF' : DARK_GREEN_COLOR}
                name={stt.phase === 'recording' ? 'stop' : 'mic-outline'}
                size={19}
              />
            )}
          </Pressable>
        </View>
      )}

      {!readOnly && stt.phase !== 'idle' && stt.phase !== 'failed' && (
        <Text accessibilityLiveRegion="polite" style={styles.sttStatusText}>
          {formatSttStatus(stt.phase, stt.durationMillis)}
        </Text>
      )}

      {!readOnly && stt.errorMessage && (
        <Text accessibilityLiveRegion="polite" accessibilityRole="alert" style={styles.errorText}>
          {stt.errorMessage}
        </Text>
      )}

      {!readOnly && stt.canRetry && (
        <Pressable
          accessibilityRole="button"
          onPress={stt.retry}
          style={({ pressed }) => [styles.sttRetryButton, pressed && styles.pressed]}
        >
          <Text style={styles.sttRetryButtonText}>다시 시도</Text>
        </Pressable>
      )}

      {!readOnly && errorMessage && <Text style={styles.errorText}>{errorMessage}</Text>}

      {!readOnly && (
        <Pressable
          accessibilityRole="button"
          disabled={readOnly || isSaving || isSttOperationActive || draftText.trim().length === 0}
          onPress={handleSaveText}
          style={({ pressed }) => [
            styles.saveButton,
            (readOnly || isSaving || isSttOperationActive || draftText.trim().length === 0) &&
              styles.saveButtonDisabled,
            pressed && styles.pressed,
          ]}
        >
          {/* 홈 화면 "내 스터디 만들기" 버튼과 같은 색 비율(진초록 표면 + 연한 민트 글씨). */}
          <GlossyFill base="#223028" glossOpacity={0.24} light="#3B4D43" radius={14} />
          {isSaving ? (
            <ActivityIndicator color={SAVE_BUTTON_TEXT_COLOR} size="small" />
          ) : (
            <Text style={styles.saveButtonText}>메모 저장</Text>
          )}
        </Pressable>
      )}

      {recordsState === 'loading' ? (
        <ActivityIndicator color={PRIMARY_COLOR} style={styles.savedLoader} />
      ) : recordsState === 'error' ? (
        <View style={styles.savedErrorBox}>
          <Text style={styles.savedEmpty}>저장된 메모를 불러오지 못했습니다.</Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => void loadRecords()}
            style={({ pressed }) => [styles.recordRetryButton, pressed && styles.pressed]}
          >
            <Text style={styles.recordRetryButtonText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : records.length > 0 ? (
        <View style={styles.savedSection}>
          <Text style={styles.savedTitle}>저장된 메모</Text>
          {records.map((record) => (
            <View key={record.sourceId} style={styles.savedCard}>
              <View style={styles.savedCardHeader}>
                <Text style={styles.savedTimestamp}>{formatTimestamp(record.createdAt)}</Text>
                {record.canDelete && !readOnly && (
                  <Pressable
                    accessibilityLabel="메모 삭제"
                    accessibilityRole="button"
                    accessibilityState={{ disabled: isSttOperationActive }}
                    disabled={isSttOperationActive}
                    hitSlop={8}
                    onPress={() => void handleDelete(record.sourceId)}
                    style={isSttOperationActive ? styles.actionIconDisabled : undefined}
                  >
                    <Ionicons color={ERROR_COLOR} name="trash-outline" size={16} />
                  </Pressable>
                )}
              </View>
              <RecordContent record={record} />
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}

function RecordContent({ record }: { record: FieldRecord }) {
  if (record.sourceType === 'PHOTO') {
    return record.photo?.available ? (
      // 기록 사진은 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 쿼리를 뗀
      // S3 객체 경로를 cacheKey로 고정해 서명이 바뀌어도 캐시 적중되게 한다
      // (커뮤니티 목록과 동일 패턴).
      <Image
        source={{ uri: record.photo.fileUrl, cacheKey: record.photo.fileUrl.split('?')[0] }}
        style={styles.savedPhoto}
        contentFit="cover"
        cachePolicy="memory-disk"
      />
    ) : (
      <View style={styles.unavailableRecord}>
        <Ionicons color={MUTED_TEXT_COLOR} name="image-outline" size={18} />
        <Text style={styles.savedContent}>현재 확인할 수 없는 사진입니다.</Text>
      </View>
    );
  }

  if (record.sourceType === 'STT') {
    const transcript = record.textContent?.trim();
    return (
      <View style={styles.sttRecordBody}>
        <Text style={styles.recordTypeLabel}>음성 메모</Text>
        <Text style={styles.savedContent}>
          {transcript ||
            (record.sttStatus === 'FAILED'
              ? '음성 변환에 실패한 기록입니다.'
              : '음성 내용을 변환하고 있습니다.')}
        </Text>
      </View>
    );
  }

  return <Text style={styles.savedContent}>{record.textContent ?? ''}</Text>;
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  const hours = date.getHours();
  const period = hours < 12 ? '오전' : '오후';
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  const minutes = String(date.getMinutes()).padStart(2, '0');

  return `${period} ${hour12}:${minutes}`;
}

function formatSttStatus(phase: ReturnType<typeof useChecklistItemStt>['phase'], duration: number) {
  if (phase === 'recovering') return '진행 중인 음성 변환을 확인하는 중…';
  if (phase === 'preparing') return '마이크를 준비하는 중…';
  if (phase === 'recording') {
    const totalSeconds = Math.floor(duration / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = String(totalSeconds % 60).padStart(2, '0');
    return `녹음 중 ${minutes}:${seconds} · 마이크를 눌러 종료`;
  }
  if (phase === 'uploading') return '음성을 안전하게 업로드하는 중…';
  if (phase === 'pending') return '음성 변환 대기 중…';
  if (phase === 'processing') return '음성을 텍스트로 변환하는 중…';
  return '';
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  backRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  backTextColumn: { flex: 1, gap: 2 },
  // ChecklistListView 의 항목 제목과 같은 규칙 — 나브바 탭 라벨과 같은 글씨체·굵기,
  // 색은 검정.
  itemTitle: {
    fontSize: 15,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: '#000000',
  },
  itemSubtitle: {
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
    lineHeight: 17,
  },
  captureRow: { flexDirection: 'row', gap: 10 },
  // 배경은 GlossyFill(구슬 질감)이 그림 — 홈 화면 "스터디 찾기" 버튼과 같은 톤·느낌.
  captureButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 9,
    borderRadius: 12,
    boxShadow: '0px 4px 12px rgba(16, 39, 30, 0.18)',
  },
  captureButtonDisabled: { opacity: 0.5 },
  captureLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  textInputShell: {
    position: 'relative',
  },
  textInput: {
    minHeight: 90,
    paddingLeft: 12,
    paddingTop: 12,
    paddingRight: 56,
    paddingBottom: 48,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    fontSize: 13,
    color: DARK_GREEN_COLOR,
    textAlignVertical: 'top',
  },
  microphoneButton: {
    position: 'absolute',
    right: 10,
    bottom: 10,
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: '#E4FFF0',
  },
  microphoneButtonRecording: {
    borderColor: ERROR_COLOR,
    backgroundColor: ERROR_COLOR,
  },
  microphoneButtonDisabled: { opacity: 0.5 },
  actionIconDisabled: { opacity: 0.4 },
  sttStatusText: {
    marginTop: -6,
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
  },
  sttRetryButton: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E4FFF0',
  },
  sttRetryButtonText: {
    fontSize: 12,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  errorText: {
    fontSize: 12,
    color: ERROR_COLOR,
  },
  // 배경은 GlossyFill(구슬 질감)이 그림 — 이전엔 진초록 배경+초록 글씨라 대비가 잘 안
  // 보였습니다. 밝은 민트 표면 위에 진초록 글씨로 바꿔 또렷하게 보이게 했습니다.
  saveButton: {
    height: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0px 4px 14px rgba(16, 39, 30, 0.2)',
  },
  saveButtonDisabled: { opacity: 0.4 },
  saveButtonText: {
    fontSize: 14,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: SAVE_BUTTON_TEXT_COLOR,
  },
  savedLoader: { marginTop: 8 },
  savedEmpty: {
    marginTop: 8,
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
  },
  savedErrorBox: { alignItems: 'flex-start', gap: 10 },
  recordRetryButton: {
    paddingHorizontal: 12,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E4FFF0',
  },
  recordRetryButtonText: { fontSize: 12, fontWeight: '700', color: DARK_GREEN_COLOR },
  savedSection: { gap: 8 },
  savedTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: MUTED_TEXT_COLOR,
  },
  savedCard: {
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    gap: 6,
  },
  savedCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  savedTimestamp: {
    fontSize: 11,
    color: MUTED_TEXT_COLOR,
  },
  savedContent: {
    fontSize: 13,
    color: DARK_GREEN_COLOR,
    lineHeight: 19,
  },
  unavailableRecord: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  sttRecordBody: { gap: 4 },
  recordTypeLabel: { fontSize: 11, fontWeight: '700', color: PRIMARY_COLOR },
  savedPhoto: {
    width: '100%',
    height: 160,
    borderRadius: 10,
    resizeMode: 'cover',
  },
  pressed: { opacity: 0.82, transform: [{ scale: 0.97 }] },
});
