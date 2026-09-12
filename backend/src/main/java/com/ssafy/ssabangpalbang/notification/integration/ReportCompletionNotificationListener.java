package com.ssafy.ssabangpalbang.notification.integration;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.entity.NotificationCategory;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportCompletionNotificationListener {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String TYPE = "REPORT_COMPLETED";
    private static final String TARGET_SCREEN = "REPORT_DETAIL";
    private static final String TITLE = "AI 임장 리포트가 완성됐어요";
    private static final String BODY = "임장 리포트가 생성되었습니다.";

    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createBeforeCommit(ReportCompletedEvent event) {
        List<StudyMember> recipients = studyMemberRepository
                .findByStudyIdAndStatus(
                        event.studyId(),
                        StudyMemberStatus.ACTIVE
                );
        if (recipients.isEmpty()) {
            throw new IllegalStateException(
                    "완료 리포트를 받을 활성 스터디원이 없습니다."
            );
        }

        OffsetDateTime sentAt = OffsetDateTime.ofInstant(
                clock.instant(),
                SEOUL_ZONE_ID
        );
        List<Long> recipientIds = recipients.stream()
                .map(StudyMember::getMemberId)
                .sorted()
                .toList();
        Map<Long, Boolean> serviceNotificationAgreements = memberRepository
                .findAllById(recipientIds)
                .stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        Member::isServiceNotificationAgreed
                ));
        recipientIds.forEach(recipientId -> createIfAbsent(
                event.reportId(),
                recipientId,
                serviceNotificationAgreements.getOrDefault(
                        recipientId,
                        false
                ),
                sentAt
        ));
    }

    private void createIfAbsent(
            Long reportId,
            Long recipientId,
            boolean serviceNotificationAgreed,
            OffsetDateTime sentAt
    ) {
        String idempotencyKey = "%s:%d:%d".formatted(
                TYPE,
                reportId,
                recipientId
        );
        if (notificationRepository.findByIdempotencyKey(idempotencyKey)
                .isPresent()) {
            return;
        }

        Notification saved = notificationRepository.saveAndFlush(
                Notification.create(
                        recipientId,
                        null,
                        NotificationCategory.REPORT,
                        TYPE,
                        TARGET_SCREEN,
                        reportId,
                        null,
                        TITLE,
                        BODY,
                        idempotencyKey,
                        sentAt
                )
        );
        eventPublisher.publishEvent(new ReportCompletionPushRequestedEvent(
                saved.getId(),
                recipientId,
                reportId,
                serviceNotificationAgreed,
                TITLE,
                BODY
        ));
    }
}
