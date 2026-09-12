package com.ssafy.ssabangpalbang.chat.event;

import com.ssafy.ssabangpalbang.chat.config.ChatPushAsyncConfig;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePushListener {

    private static final int MAX_TEXT_PREVIEW_LENGTH = 80;

    private final StudyRepository studyRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;

    @Async(ChatPushAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(ChatMessagePushRequestedEvent event) {
        if (!fcmPushGateway.isReady()) {
            return;
        }

        try {
            Study study = studyRepository.findByIdAndDeletedAtIsNull(event.studyId())
                    .orElse(null);
            if (study == null) {
                return;
            }

            List<StudyMember> recipients = studyMemberRepository
                    .findByStudyIdAndStatus(event.studyId(), StudyMemberStatus.ACTIVE)
                    .stream()
                    .filter(studyMember -> !studyMember.getMemberId().equals(event.senderId()))
                    .filter(StudyMember::isChatPushEnabled)
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }

            Map<Long, Member> membersById = memberRepository.findAllById(
                            recipients.stream().map(StudyMember::getMemberId).toList()
                    ).stream()
                    .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                    .filter(member -> member.getDeletedAt() == null)
                    .filter(Member::isServiceNotificationAgreed)
                    .collect(Collectors.toMap(Member::getId, Function.identity()));
            if (membersById.isEmpty()) {
                return;
            }

            Map<String, String> data = Map.of(
                    "notificationType", "STUDY_CHAT_MESSAGE",
                    "targetScreen", "STUDY_CHAT",
                    "studyId", event.studyId().toString(),
                    "messageId", event.messageId().toString()
            );
            String body = buildBody(event);
            Map<Long, List<FcmToken>> tokensByMemberId = fcmTokenRepository
                    .findAllByMemberIdIn(membersById.keySet())
                    .stream()
                    .collect(Collectors.groupingBy(FcmToken::getMemberId));
            for (StudyMember recipient : recipients) {
                Member member = membersById.get(recipient.getMemberId());
                if (member == null) {
                    continue;
                }
                for (FcmToken token : tokensByMemberId.getOrDefault(member.getId(), List.of())) {
                    send(token, study.getTitle(), body, data, event, member.getId());
                }
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "채팅 FCM 후처리에 실패했습니다. messageId={}, studyId={}, reason=internal_error",
                    event.messageId(),
                    event.studyId(),
                    exception
            );
        }
    }

    private String buildBody(ChatMessagePushRequestedEvent event) {
        if ("IMAGE".equals(event.messageType())) {
            return event.senderNickname() + "님이 사진을 보냈어요.";
        }
        String content = event.content() == null ? "" : event.content().strip();
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount > MAX_TEXT_PREVIEW_LENGTH) {
            int previewEnd = content.offsetByCodePoints(0, MAX_TEXT_PREVIEW_LENGTH - 3);
            content = content.substring(0, previewEnd) + "...";
        }
        return event.senderNickname() + ": " + content;
    }

    private void send(
            FcmToken token,
            String title,
            String body,
            Map<String, String> data,
            ChatMessagePushRequestedEvent event,
            Long recipientId
    ) {
        try {
            fcmPushGateway.sendNotification(token.getToken(), title, body, data);
        } catch (RuntimeException exception) {
            log.warn(
                    "채팅 FCM 전송에 실패했습니다. messageId={}, recipientId={}, deviceId={}, reason=provider_rejected",
                    event.messageId(),
                    recipientId,
                    token.getDeviceId(),
                    exception
            );
        }
    }
}
