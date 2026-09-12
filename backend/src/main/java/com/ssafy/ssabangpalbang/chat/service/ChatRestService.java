package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.domain.ChatReadStatus;
import com.ssafy.ssabangpalbang.chat.domain.MessageType;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageDeleteResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageItemResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageListResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.event.ChatMessageBroadcastEvent;
import com.ssafy.ssabangpalbang.chat.dto.ChatNotificationSettingResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatUnreadCountResponse;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageHistoryRow;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import com.ssafy.ssabangpalbang.chat.repository.ChatReadStatusRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * BE-012 채팅 REST API(이력 조회·읽음 처리·안 읽은 수 조회)의 서비스 계층이다.
 */
@Service
@RequiredArgsConstructor
public class ChatRestService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final MediaPresignedUrlProvider mediaPresignedUrlProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ChatMessageListResponse getHistory(
            Long studyId,
            Long memberId,
            Long cursor,
            Integer size
    ) {
        int pageSize = resolveSize(size);
        requireActiveMember(memberId);
        requireActiveStudyMember(studyId, memberId);

        List<ChatMessageHistoryRow> rows =
                chatMessageRepository.findHistoryPage(studyId, cursor, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<ChatMessageHistoryRow> page = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, String> imageUrls = resolveImageUrls(page);
        List<ChatMessageItemResponse> content = page.stream()
                .map(row -> ChatMessageItemResponse.from(
                        row,
                        row.getImageFileId() == null ? null : imageUrls.get(row.getImageFileId())
                ))
                .toList();

        Long nextCursor = hasNext && !content.isEmpty()
                ? content.get(content.size() - 1).messageId()
                : null;

        return new ChatMessageListResponse(studyId, content, nextCursor, hasNext);
    }

    /**
     * chat_read_status를 원자적 upsert(INSERT ... ON CONFLICT ... DO UPDATE)로 갱신한다.
     *
     * <p>기존의 "조회 → 없으면 save / 있으면 advanceLastReadAt(dirty checking)" 구조는
     * 읽음 상태가 없는 상태에서 동시 요청이 들어오면 둘 다 INSERT를 시도해
     * UNIQUE(study_id, member_id) 위반으로 한쪽이 실패하거나, 두 트랜잭션이 각자
     * 조회한 과거 엔티티를 기준으로 dirty checking하면서 더 늦게 커밋된 쪽이
     * 더 최신 시각을 과거 시각으로 덮어쓸 수 있는 경쟁 상태가 있었다. PostgreSQL의
     * 원자적 upsert(단일 SQL 문)로 대체해 이 두 경쟁 상태를 모두 DB 레벨에서 제거한다.</p>
     */
    @Transactional
    public ChatReadResponse markRead(Long studyId, Long memberId, ChatReadRequest request) {
        requireActiveMember(memberId);
        requireActiveStudyMember(studyId, memberId);

        Instant lastReadAt = resolveLastReadAt(studyId, request.lastReadMessageId());

        chatReadStatusRepository.upsertLastReadAt(studyId, memberId, lastReadAt);

        // upsert는 영속성 컨텍스트를 거치지 않는 네이티브 쓰기이므로,
        // 응답에는 요청값이 아니라 upsert 직후 DB에 실제로 저장된 값을 재조회해 반환한다.
        Instant savedLastReadAt = chatReadStatusRepository
                .findByStudyIdAndMemberId(studyId, memberId)
                .map(ChatReadStatus::getLastReadAt)
                .orElseThrow(() -> new IllegalStateException(
                        "chat_read_status upsert 직후 조회에 실패했습니다. studyId="
                                + studyId + ", memberId=" + memberId
                ));

        return new ChatReadResponse(
                studyId,
                OffsetDateTime.ofInstant(savedLastReadAt, SEOUL),
                0
        );
    }

    @Transactional(readOnly = true)
    public ChatUnreadCountResponse getUnreadCount(Long studyId, Long memberId) {
        requireActiveMember(memberId);
        requireActiveStudyMember(studyId, memberId);

        Instant lastReadAt = chatReadStatusRepository.findByStudyIdAndMemberId(studyId, memberId)
                .map(ChatReadStatus::getLastReadAt)
                .orElse(null);

        long unreadCount = chatMessageRepository.countUnreadExcludingSender(
                studyId, memberId, lastReadAt
        );

        return new ChatUnreadCountResponse(
                studyId,
                (int) unreadCount,
                lastReadAt == null ? null : OffsetDateTime.ofInstant(lastReadAt, SEOUL)
        );
    }

    @Transactional(readOnly = true)
    public ChatNotificationSettingResponse getNotificationSetting(
            Long studyId,
            Long memberId
    ) {
        requireActiveMember(memberId);
        StudyMember studyMember = requireActiveStudyMember(studyId, memberId);
        return new ChatNotificationSettingResponse(
                studyId,
                studyMember.isChatPushEnabled()
        );
    }

    @Transactional
    public ChatNotificationSettingResponse updateNotificationSetting(
            Long studyId,
            Long memberId,
            boolean pushEnabled
    ) {
        requireActiveMember(memberId);
        StudyMember studyMember = requireActiveStudyMemberForUpdate(studyId, memberId);
        studyMember.updateChatPushEnabled(pushEnabled);
        return new ChatNotificationSettingResponse(studyId, pushEnabled);
    }

    /**
     * 본인이 보낸 메시지를 소프트 삭제한다.
     *
     * <p>행을 지우지 않고 deleted_at만 기록한다 — 커서 페이지네이션과 안 읽은 수
     * 계산이 흔들리지 않고, 이력에는 톰스톤으로 남는다. 커밋 후에는 실시간 구독자에게
     * {@code deleted=true} 페이로드를 발행해 열려 있는 채팅방에서도 즉시 지워진다.</p>
     *
     * <p>SYSTEM 메시지는 senderId가 없어 소유자 검사에서 자연히 걸러진다.
     * COMPLETED·CANCELED 스터디는 읽기 전용 보관이므로 SEND와 같은 기준으로 차단한다.
     * 이미 삭제된 메시지는 재브로드캐스트 없이 성공으로 응답한다(재시도 멱등).</p>
     */
    @Transactional
    public ChatMessageDeleteResponse deleteMessage(Long studyId, Long memberId, Long messageId) {
        requireActiveMember(memberId);
        requireActiveStudyMember(studyId, memberId);
        requireStudyOpenForWrite(studyId);

        ChatMessage message = chatMessageRepository.findByIdAndStudyId(messageId, studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

        if (message.getSenderId() == null || !message.getSenderId().equals(memberId)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_DELETE_FORBIDDEN);
        }

        boolean alreadyDeleted = message.isDeleted();
        message.markDeleted(Instant.now());

        if (!alreadyDeleted) {
            ChatSenderResponse sender = memberRepository.findById(memberId)
                    .map(ChatSenderResponse::from)
                    .orElse(null);
            eventPublisher.publishEvent(new ChatMessageBroadcastEvent(
                    ChatMessageBroadcastResponse.deleted(message, sender)
            ));
        }

        return new ChatMessageDeleteResponse(
                studyId,
                messageId,
                OffsetDateTime.ofInstant(message.getDeletedAt(), SEOUL)
        );
    }

    /**
     * 본인이 보낸 TEXT 메시지의 본문을 수정한다.
     *
     * <p>IMAGE·SYSTEM은 수정 대상이 아니다(사진 교체는 삭제 후 재전송).
     * 삭제된 메시지는 이용자 입장에서 더 이상 존재하지 않으므로 404로 감춘다.
     * 커밋 후 실시간 구독자에게 바뀐 본문과 editedAt을 발행해 열려 있는
     * 채팅방에서도 즉시 갱신된다.</p>
     */
    @Transactional
    public ChatMessageEditResponse editMessage(
            Long studyId,
            Long memberId,
            Long messageId,
            ChatMessageEditRequest request
    ) {
        requireActiveMember(memberId);
        requireActiveStudyMember(studyId, memberId);
        requireStudyOpenForWrite(studyId);

        ChatMessage message = chatMessageRepository.findByIdAndStudyId(messageId, studyId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

        if (message.getSenderId() == null || !message.getSenderId().equals(memberId)) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_EDIT_FORBIDDEN);
        }
        if (message.getMessageType() != MessageType.TEXT) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TYPE_INVALID);
        }

        message.editContent(request.content().strip(), Instant.now());

        ChatSenderResponse sender = memberRepository.findById(memberId)
                .map(ChatSenderResponse::from)
                .orElse(null);
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(
                ChatMessageBroadcastResponse.edited(message, sender)
        ));

        return new ChatMessageEditResponse(
                studyId,
                messageId,
                message.getContent(),
                OffsetDateTime.ofInstant(message.getEditedAt(), SEOUL)
        );
    }

    /** COMPLETED·CANCELED 스터디는 읽기 전용이다. SEND 차단(isStudyOpenForSend)과 같은 기준. */
    private void requireStudyOpenForWrite(Long studyId) {
        studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .filter(study -> study.getStatus() != StudyStatus.COMPLETED
                        && study.getStatus() != StudyStatus.CANCELED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_STUDY_COMPLETED));
    }

    private Instant resolveLastReadAt(Long studyId, Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return Instant.now();
        }
        ChatMessage message = chatMessageRepository.findByIdAndStudyId(lastReadMessageId, studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHAT_MESSAGE_INVALID,
                        Map.of(
                                "field", "lastReadMessageId",
                                "reason", "해당 스터디의 메시지가 아닙니다."
                        )
                ));
        return message.getCreatedAt();
    }

    private Map<Long, String> resolveImageUrls(List<ChatMessageHistoryRow> rows) {
        // 삭제된 메시지의 사진은 응답에 싣지 않으므로 presigned URL도 발급하지 않는다.
        List<Long> imageFileIds = rows.stream()
                .filter(row -> row.getDeletedAt() == null)
                .map(ChatMessageHistoryRow::getImageFileId)
                .filter(id -> id != null)
                .toList();
        if (imageFileIds.isEmpty() || !mediaPresignedUrlProvider.isEnabled()) {
            return Map.of();
        }

        return mediaPresignedUrlProvider
                .generateGetUrls(imageFileIds);
    }

    private void requireActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private StudyMember requireActiveStudyMember(Long studyId, Long memberId) {
        studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        return studyMemberRepository.findByStudyIdAndMemberId(studyId, memberId)
                .filter(studyMember -> studyMember.getStatus() == StudyMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
    }

    private StudyMember requireActiveStudyMemberForUpdate(Long studyId, Long memberId) {
        studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));

        return studyMemberRepository.findForUpdateByStudyIdAndMemberId(studyId, memberId)
                .filter(studyMember -> studyMember.getStatus() == StudyMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
    }

    private int resolveSize(Integer size) {
        int resolved = size == null ? DEFAULT_SIZE : size;
        if (resolved < 1 || resolved > MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "size", "reason", "조회 개수는 1 이상 50 이하이어야 합니다.")
            );
        }
        return resolved;
    }
}
