package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldRecordResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldRecordUpdateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordListResponse;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldRecordMutationResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldRecordService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FieldVisitAccessService fieldVisitAccessService;
    private final ChecklistItemRepository checklistItemRepository;
    private final FieldRecordRepository fieldRecordRepository;
    private final FieldRecordWriter fieldRecordWriter;
    private final FieldPhotoValidator fieldPhotoValidator;
    private final FieldRecordCountRepository fieldRecordCountRepository;
    private final FileMetaRepository fileMetaRepository;
    private final MediaAccessUrlProvider mediaAccessUrlProvider;
    private final MemberRepository memberRepository;

    public CreateResult create(
            Long studyId,
            Long memberId,
            FieldRecordCreateRequest request
    ) {
        FieldVisitParticipation participation =
                fieldVisitAccessService.requireWritableParticipation(
                        studyId,
                        memberId,
                        ErrorCode.FIELD_RECORD_CREATE_FORBIDDEN,
                        FieldVisitAccessService.FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE
                );

        String clientRequestId = normalizeClientRequestId(request.clientRequestId());
        FieldRecordSourceType sourceType = parseCreateSourceType(request.sourceType());
        Long sessionId = participation.session().getId();
        validateOwnedItem(request.checklistItemId(), sessionId, memberId);

        Instant now = Instant.now();
        String fingerprint;
        FieldRecord draft;
        if (sourceType == FieldRecordSourceType.TEXT) {
            if (request.photoFileId() != null) {
                throw new BusinessException(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
            }
            String text = requireNonBlankText(request.textContent());
            fingerprint = FieldRecordFingerprint.ofText(request.checklistItemId(), text);
            draft = FieldRecord.createText(
                    sessionId,
                    request.checklistItemId(),
                    memberId,
                    text,
                    clientRequestId,
                    fingerprint,
                    now
            );
        } else {
            if (request.textContent() != null && !request.textContent().isBlank()) {
                throw new BusinessException(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
            }
            FileMeta photo = fieldPhotoValidator.requireValidFieldPhoto(
                    request.photoFileId(),
                    memberId,
                    studyId
            );
            fingerprint = FieldRecordFingerprint.ofPhoto(request.checklistItemId(), photo.getId());
            draft = FieldRecord.createPhoto(
                    sessionId,
                    request.checklistItemId(),
                    memberId,
                    photo.getId(),
                    clientRequestId,
                    fingerprint,
                    now
            );
        }

        try {
            FieldRecord saved = fieldRecordWriter.saveAndFlush(draft);
            return new CreateResult(
                    HttpStatus.CREATED,
                    FieldRecordResponseCode.FIELD_RECORD_CREATE_SUCCESS,
                    toCreateResponse(studyId, memberId, saved, true)
            );
        } catch (DataIntegrityViolationException ex) {
            FieldRecord existing = fieldRecordRepository
                    .findByClientRequestId(clientRequestId)
                    .orElseThrow(() -> ex);
            assertIdempotentMatch(
                    existing,
                    studyId,
                    sessionId,
                    memberId,
                    request.checklistItemId(),
                    sourceType,
                    fingerprint,
                    clientRequestId
            );
            return new CreateResult(
                    HttpStatus.OK,
                    FieldRecordResponseCode.FIELD_RECORD_ALREADY_CREATED,
                    toCreateResponse(studyId, memberId, existing, true)
            );
        }
    }

    @Transactional(readOnly = true)
    public FieldRecordListResponse list(
            Long studyId,
            Long memberId,
            Long checklistItemId,
            String sourceTypeRaw,
            Boolean mineOnlyParam,
            Long cursor,
            Integer sizeParam
    ) {
        if (checklistItemId != null && checklistItemId < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (cursor != null && cursor < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        FieldVisitReadStatus readStatus = fieldVisitAccessService.readRecordStatus(
                studyId,
                memberId,
                ErrorCode.FIELD_RECORD_ACCESS_DENIED
        );
        if (!readStatus.isSessionStarted()) {
            return new FieldRecordListResponse(
                    studyId, null, false, null, List.of(), null, false
            );
        }

        boolean mineOnly = mineOnlyParam == null || mineOnlyParam;
        FieldRecordSourceType sourceType = null;
        if (sourceTypeRaw != null && !sourceTypeRaw.isBlank()) {
            try {
                sourceType = FieldRecordSourceType.from(sourceTypeRaw);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.FIELD_RECORD_SOURCE_TYPE_INVALID);
            }
        }

        FieldRecordListResponse.ChecklistItemSummary itemSummary = null;
        if (checklistItemId != null) {
            ChecklistItem item = checklistItemRepository
                    .findByIdAndSessionId(checklistItemId, readStatus.sessionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_ITEM_NOT_FOUND));
            itemSummary = new FieldRecordListResponse.ChecklistItemSummary(
                    item.getId(),
                    item.getCategory(),
                    item.getTitle(),
                    item.getSubtitle()
            );
        }

        int size = sizeParam == null ? DEFAULT_SIZE : sizeParam;
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<FieldRecord> fetched = fieldRecordRepository.findPage(
                readStatus.sessionId(),
                memberId,
                mineOnly,
                checklistItemId,
                sourceType,
                cursor,
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = fetched.size() > size;
        List<FieldRecord> page = hasNext ? fetched.subList(0, size) : fetched;

        Map<Long, FileMeta> photos = loadPhotos(page);
        Map<Long, MediaAccessUrl> urls = mediaAccessUrlProvider.issueAll(photos.keySet());
        Map<Long, Member> authors = loadAuthors(page);
        boolean writable = isWritable(readStatus);

        List<FieldRecordListResponse.RecordItem> content = new ArrayList<>();
        for (FieldRecord record : page) {
            boolean isMine = memberId.equals(record.getAuthorId());
            Permissions permissions = computePermissions(record, memberId, writable);
            content.add(new FieldRecordListResponse.RecordItem(
                    record.getId(),
                    record.getChecklistItemId(),
                    record.getSourceType().name(),
                    record.getTextContent(),
                    toPhotoInfo(record, photos, urls),
                    record.getSttStatus(),
                    toAuthor(authors.get(record.getAuthorId())),
                    isMine,
                    permissions.canEdit(),
                    permissions.canDelete(),
                    toOffset(record.getCreatedAt()),
                    toOffset(record.getUpdatedAt())
            ));
        }

        String nextCursor = hasNext && !page.isEmpty()
                ? String.valueOf(page.get(page.size() - 1).getId())
                : null;

        return new FieldRecordListResponse(
                studyId,
                readStatus.sessionId(),
                readStatus.readOnly(),
                itemSummary,
                content,
                nextCursor,
                hasNext
        );
    }

    @Transactional
    public FieldRecordMutationResponse update(
            Long studyId,
            Long memberId,
            Long recordId,
            FieldRecordUpdateRequest request
    ) {
        FieldVisitParticipation participation =
                fieldVisitAccessService.requireWritableParticipation(
                        studyId,
                        memberId,
                        ErrorCode.FIELD_RECORD_UPDATE_FORBIDDEN,
                        FieldVisitAccessService.FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE
                );

        if (request.checklistItemId() == null
                && request.textContent() == null
                && request.photoFileId() == null) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_UPDATE_EMPTY);
        }

        FieldRecord record = fieldRecordRepository
                .findByIdAndSessionIdForUpdate(recordId, participation.session().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_RECORD_NOT_FOUND));

        if (record.isDeleted()) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_NOT_FOUND);
        }
        if (!memberId.equals(record.getAuthorId())) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_UPDATE_FORBIDDEN);
        }
        assertEditable(record);

        Instant now = Instant.now();
        if (request.checklistItemId() != null
                && !request.checklistItemId().equals(record.getChecklistItemId())) {
            validateOwnedItem(
                    request.checklistItemId(),
                    participation.session().getId(),
                    memberId
            );
            record.updateChecklistItemId(request.checklistItemId(), now);
        }

        switch (record.getSourceType()) {
            case TEXT -> {
                if (request.photoFileId() != null) {
                    throw new BusinessException(ErrorCode.FIELD_RECORD_UPDATE_FIELD_INVALID);
                }
                if (request.textContent() != null) {
                    record.updateTextContent(requireNonBlankText(request.textContent()), now);
                }
            }
            case PHOTO -> {
                if (request.textContent() != null) {
                    throw new BusinessException(ErrorCode.FIELD_RECORD_UPDATE_FIELD_INVALID);
                }
                if (request.photoFileId() != null) {
                    FileMeta photo = fieldPhotoValidator.requireValidFieldPhoto(
                            request.photoFileId(),
                            memberId,
                            studyId
                    );
                    record.updatePhotoFileId(photo.getId(), now);
                }
            }
            case STT -> {
                if (request.photoFileId() != null) {
                    throw new BusinessException(ErrorCode.FIELD_RECORD_UPDATE_FIELD_INVALID);
                }
                if (request.textContent() != null) {
                    record.updateTextContent(requireNonBlankText(request.textContent()), now);
                }
            }
        }

        FieldRecord saved = fieldRecordRepository.save(record);
        return toMutationResponse(studyId, memberId, saved, true);
    }

    @Transactional
    public MutationResult delete(
            Long studyId,
            Long memberId,
            Long recordId
    ) {
        FieldVisitParticipation participation =
                fieldVisitAccessService.requireWritableParticipation(
                        studyId,
                        memberId,
                        ErrorCode.FIELD_RECORD_DELETE_FORBIDDEN,
                        FieldVisitAccessService.FIELD_RECORD_PARTICIPANT_ENDED_MESSAGE
                );

        FieldRecord record = fieldRecordRepository
                .findByIdAndSessionIdForUpdate(recordId, participation.session().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_RECORD_NOT_FOUND));

        if (!memberId.equals(record.getAuthorId())) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_DELETE_FORBIDDEN);
        }

        if (record.isDeleted()) {
            return new MutationResult(
                    HttpStatus.OK,
                    FieldRecordResponseCode.FIELD_RECORD_ALREADY_DELETED,
                    toMutationResponse(studyId, memberId, record, true)
            );
        }
        if (record.isSttInProgress()) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_STT_PROCESSING);
        }

        record.softDelete(Instant.now());
        FieldRecord saved = fieldRecordRepository.save(record);
        return new MutationResult(
                HttpStatus.OK,
                FieldRecordResponseCode.FIELD_RECORD_DELETE_SUCCESS,
                toMutationResponse(studyId, memberId, saved, true)
        );
    }

    private void assertEditable(FieldRecord record) {
        if (record.getSourceType() != FieldRecordSourceType.STT) {
            return;
        }
        if (record.isSttInProgress()) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_STT_PROCESSING);
        }
        if (record.isSttFailed()) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_STT_UPDATE_NOT_ALLOWED);
        }
        if (!record.isSttDone()) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_STT_PROCESSING);
        }
    }

    private void assertIdempotentMatch(
            FieldRecord existing,
            Long studyId,
            Long sessionId,
            Long memberId,
            Long checklistItemId,
            FieldRecordSourceType sourceType,
            String fingerprint,
            String clientRequestId
    ) {
        List<String> mismatchReasons = new ArrayList<>();
        if (!Objects.equals(existing.getSessionId(), sessionId)) {
            mismatchReasons.add("sessionId");
        }
        if (!Objects.equals(existing.getAuthorId(), memberId)) {
            mismatchReasons.add("authorId");
        }
        if (!Objects.equals(existing.getChecklistItemId(), checklistItemId)) {
            mismatchReasons.add("checklistItemId");
        }
        if (existing.getSourceType() != sourceType) {
            mismatchReasons.add("sourceType");
        }
        if (!existing.matchesFingerprint(fingerprint)) {
            mismatchReasons.add("fingerprint");
        }
        if (mismatchReasons.isEmpty()) {
            return;
        }
        log.warn(
                "event=FIELD_RECORD_IDEMPOTENCY_MISMATCH clientRequestId={} memberId={} studyId={} sessionId={} checklistItemId={} sourceType={} mismatchReasons={}",
                clientRequestId,
                memberId,
                studyId,
                sessionId,
                checklistItemId,
                sourceType,
                mismatchReasons
        );
        throw new BusinessException(ErrorCode.FIELD_RECORD_IDEMPOTENCY_KEY_REUSED);
    }

    private void validateOwnedItem(Long itemId, Long sessionId, Long memberId) {
        checklistItemRepository.findOwnedItem(itemId, sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_ITEM_NOT_FOUND));
    }

    private static FieldRecordSourceType parseCreateSourceType(String raw) {
        FieldRecordSourceType type;
        try {
            type = FieldRecordSourceType.from(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_SOURCE_TYPE_INVALID);
        }
        if (type == FieldRecordSourceType.STT) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    private static String requireNonBlankText(String textContent) {
        if (textContent == null) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
        }
        String trimmed = textContent.trim();
        if (trimmed.isEmpty() || trimmed.length() > FieldRecord.TEXT_CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
        }
        return trimmed;
    }

    private static String normalizeClientRequestId(String raw) {
        try {
            return UUID.fromString(raw.trim()).toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_PAYLOAD_INVALID);
        }
    }

    private static boolean isWritable(FieldVisitReadStatus readStatus) {
        return !readStatus.readOnly()
                && readStatus.participant() != null
                && readStatus.participant().getStatus().name().equals("IN_PROGRESS")
                && readStatus.session() != null
                && readStatus.session().getStatus().name().equals("IN_PROGRESS");
    }

    static Permissions computePermissions(
            FieldRecord record,
            Long memberId,
            boolean writable
    ) {
        if (!writable
                || record.isDeleted()
                || !memberId.equals(record.getAuthorId())) {
            return new Permissions(false, false);
        }
        if (record.getSourceType() == FieldRecordSourceType.STT) {
            if (record.isSttDone()) {
                return new Permissions(true, true);
            }
            if (record.isSttFailed()) {
                return new Permissions(false, true);
            }
            return new Permissions(false, false);
        }
        return new Permissions(true, true);
    }

    private FieldRecordCreateResponse toCreateResponse(
            Long studyId,
            Long memberId,
            FieldRecord record,
            boolean writable
    ) {
        Map<Long, FileMeta> photos = loadPhotos(List.of(record));
        Map<Long, MediaAccessUrl> urls = mediaAccessUrlProvider.issueAll(photos.keySet());
        Map<Long, Member> authors = loadAuthors(List.of(record));
        Permissions permissions = computePermissions(record, memberId, writable);
        Integer itemRecordCount = fieldRecordCountRepository
                .countByAuthorAndItemIds(memberId, List.of(record.getChecklistItemId()))
                .getOrDefault(record.getChecklistItemId(), 0);

        return new FieldRecordCreateResponse(
                studyId,
                record.getSessionId(),
                new FieldRecordCreateResponse.RecordBody(
                        record.getId(),
                        record.getChecklistItemId(),
                        record.getSourceType().name(),
                        record.getTextContent(),
                        toPhotoInfo(record, photos, urls),
                        record.getSttStatus(),
                        toAuthor(authors.get(record.getAuthorId())),
                        memberId.equals(record.getAuthorId()),
                        permissions.canEdit(),
                        permissions.canDelete(),
                        record.getClientRequestId(),
                        toOffset(record.getCreatedAt()),
                        toOffset(record.getUpdatedAt())
                ),
                itemRecordCount
        );
    }

    private FieldRecordMutationResponse toMutationResponse(
            Long studyId,
            Long memberId,
            FieldRecord record,
            boolean writableContext
    ) {
        Map<Long, FileMeta> photos = loadPhotos(List.of(record));
        Map<Long, MediaAccessUrl> urls = mediaAccessUrlProvider.issueAll(photos.keySet());
        Map<Long, Member> authors = loadAuthors(List.of(record));
        Map<Long, Integer> counts = fieldRecordCountRepository.countByAuthorAndItemIds(
                memberId,
                List.of(record.getChecklistItemId())
        );
        return new FieldRecordMutationResponse(
                studyId,
                record.getSessionId(),
                record.getId(),
                record.getChecklistItemId(),
                toAuthor(authors.get(record.getAuthorId())),
                record.getSourceType().name(),
                record.getTextContent(),
                record.isDeleted() ? null : toPhotoInfo(record, photos, urls),
                record.getSttStatus(),
                record.getClientRequestId(),
                record.isDeleted(),
                toOffset(record.getDeletedAt()),
                counts.getOrDefault(record.getChecklistItemId(), 0),
                toOffset(record.getCreatedAt()),
                toOffset(record.getUpdatedAt())
        );
    }

    private Map<Long, FileMeta> loadPhotos(List<FieldRecord> records) {
        Set<Long> ids = records.stream()
                .map(FieldRecord::getPhotoFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return fileMetaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FileMeta::getId, Function.identity()));
    }

    private Map<Long, Member> loadAuthors(List<FieldRecord> records) {
        Set<Long> ids = records.stream()
                .map(FieldRecord::getAuthorId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Member> map = new HashMap<>();
        for (Member member : memberRepository.findAllById(ids)) {
            map.put(member.getId(), member);
        }
        return map;
    }

    private static FieldRecordCreateResponse.PhotoInfo toPhotoInfo(
            FieldRecord record,
            Map<Long, FileMeta> photos,
            Map<Long, MediaAccessUrl> urls
    ) {
        if (record.getPhotoFileId() == null) {
            return null;
        }
        FileMeta file = photos.get(record.getPhotoFileId());
        if (file == null || file.getDeletedAt() != null) {
            return new FieldRecordCreateResponse.PhotoInfo(
                    record.getPhotoFileId(), null, null, null, false, null
            );
        }
        MediaAccessUrl url = urls.get(file.getId());
        return new FieldRecordCreateResponse.PhotoInfo(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                url == null ? null : url.url(),
                url != null,
                url == null ? null : toOffset(url.expiresAt())
        );
    }

    private static FieldRecordListResponse.Author toAuthor(Member member) {
        if (member == null) {
            return new FieldRecordListResponse.Author(null, null, null);
        }
        return new FieldRecordListResponse.Author(
                member.getId(),
                member.getNickname(),
                member.getSelectedCharacterId()
        );
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }

    record Permissions(boolean canEdit, boolean canDelete) {
    }

    public record CreateResult(
            HttpStatus httpStatus,
            FieldRecordResponseCode responseCode,
            FieldRecordCreateResponse body
    ) {
    }

    public record MutationResult(
            HttpStatus httpStatus,
            FieldRecordResponseCode responseCode,
            FieldRecordMutationResponse body
    ) {
    }
}
