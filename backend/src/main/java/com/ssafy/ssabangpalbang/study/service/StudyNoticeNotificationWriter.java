package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationCommandRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import com.ssafy.ssabangpalbang.study.event.StudyNoticePushRequestedEvent;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StudyNoticeNotificationWriter {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int BODY_PREVIEW_MAX_CODE_POINTS = 120;
    private static final String TYPE = "STUDY_NOTICE_CREATED";
    private static final String TARGET_SCREEN = "STUDY_DETAIL";

    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final NotificationCommandRepository notificationCommandRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void notifyCreated(Study study, StudyNotice notice, Long actorId) {
        List<Long> recipientIds = studyMemberRepository
                .findByStudyIdAndStatus(study.getId(), StudyMemberStatus.ACTIVE)
                .stream()
                .map(StudyMember::getMemberId)
                .filter(memberId -> !memberId.equals(actorId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        Map<Long, Member> recipientsById = memberRepository.findAllById(recipientIds).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        if (recipientsById.isEmpty()) {
            return;
        }

        String title = "새 스터디 공지가 등록되었습니다.";
        String body = "'%s' 스터디: %s".formatted(
                study.getTitle(),
                preview(notice.getContent())
        );
        OffsetDateTime sentAt = OffsetDateTime.now(SEOUL_ZONE_ID);

        for (Long recipientId : recipientIds) {
            Member recipient = recipientsById.get(recipientId);
            if (recipient == null) {
                continue;
            }
            notificationCommandRepository.insertIfAbsent(Notification.create(
                            recipientId,
                            actorId,
                            NotificationCategory.STUDY,
                            TYPE,
                            TARGET_SCREEN,
                            study.getId(),
                            notice.getId(),
                            title,
                            body,
                            "%s:%d:%d".formatted(TYPE, notice.getId(), recipientId),
                            sentAt
                    ))
                    .ifPresent(notificationId -> eventPublisher.publishEvent(
                            new StudyNoticePushRequestedEvent(
                                    notificationId,
                                    recipientId,
                                    study.getId(),
                                    notice.getId(),
                                    title,
                                    body,
                                    recipient.isServiceNotificationAgreed()
                            )
                    ));
        }
    }

    private static String preview(String content) {
        String normalized = content.strip();
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= BODY_PREVIEW_MAX_CODE_POINTS) {
            return normalized;
        }
        int previewEnd = normalized.offsetByCodePoints(0, BODY_PREVIEW_MAX_CODE_POINTS - 3);
        return normalized.substring(0, previewEnd) + "...";
    }
}
