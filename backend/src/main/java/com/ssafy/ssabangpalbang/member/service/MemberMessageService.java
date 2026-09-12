package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberMessageSendRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResult;
import com.ssafy.ssabangpalbang.member.repository.FollowRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberMessageService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String MESSAGE_TITLE = "새로운 메시지가 도착했어요";
    private static final String IDEMPOTENCY_KEY_PREFIX = "member-message:";
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final MemberMessageWriter memberMessageWriter;

    public MemberMessageSendResult send(
            Long senderId,
            Long recipientId,
            MemberMessageSendRequest request
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String content = normalizeContent(request.content());
        String clientMessageId = normalizeClientMessageId(
                request.clientMessageId()
        );
        Member sender = requireMember(senderId);
        validateActiveSender(sender);
        if (senderId.equals(recipientId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_MESSAGE_SELF_NOT_ALLOWED
            );
        }

        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX
                + senderId
                + ":"
                + clientMessageId;
        Notification existing = notificationRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existingResult(existing, clientMessageId);
        }

        Member recipient = requireActiveRecipient(recipientId);
        if (followRepository.findByFollowerIdAndFollowingId(
                senderId,
                recipientId
        ).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEMBER_MESSAGE_FOLLOW_REQUIRED
            );
        }

        try {
            Notification saved = memberMessageWriter.save(
                    senderId,
                    recipientId,
                    recipient.isServiceNotificationAgreed(),
                    MESSAGE_TITLE,
                    content,
                    idempotencyKey,
                    OffsetDateTime.now(SEOUL_ZONE_ID)
            );
            return new MemberMessageSendResult(
                    MemberResponseCode.MESSAGE_SENT,
                    MemberMessageSendResponse.of(
                            saved,
                            recipient,
                            clientMessageId
                    ),
                    true
            );
        } catch (DataIntegrityViolationException exception) {
            Notification concurrentExisting = notificationRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            return existingResult(concurrentExisting, clientMessageId);
        }
    }

    private MemberMessageSendResult existingResult(
            Notification notification,
            String clientMessageId
    ) {
        Member recipient = requireMember(notification.getRecipientId());
        return new MemberMessageSendResult(
                MemberResponseCode.MESSAGE_ALREADY_SENT,
                MemberMessageSendResponse.of(
                        notification,
                        recipient,
                        clientMessageId
                ),
                false
        );
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private Member requireActiveRecipient(Long recipientId) {
        Member recipient = requireMember(recipientId);
        if (recipient.getStatus() != MemberStatus.ACTIVE
                || recipient.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return recipient;
    }

    private void validateActiveSender(Member sender) {
        if (sender.getStatus() != MemberStatus.ACTIVE
                || sender.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEMBER_MESSAGE_CONTENT_REQUIRED,
                    Map.of("field", "content")
            );
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.MEMBER_MESSAGE_CONTENT_TOO_LONG,
                    Map.of(
                            "field", "content",
                            "maxLength", MAX_CONTENT_LENGTH
                    )
            );
        }
        return normalized;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        String normalized = clientMessageId == null
                ? ""
                : clientMessageId.strip();
        try {
            UUID parsed = UUID.fromString(normalized);
            if (!parsed.toString().equalsIgnoreCase(normalized)) {
                throw invalidClientMessageId();
            }
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            throw invalidClientMessageId();
        }
    }

    private BusinessException invalidClientMessageId() {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", "clientMessageId",
                        "reason", "올바른 UUID 형식이 아닙니다."
                )
        );
    }
}
