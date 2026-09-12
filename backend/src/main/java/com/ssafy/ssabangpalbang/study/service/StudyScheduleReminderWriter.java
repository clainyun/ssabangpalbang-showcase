package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.dto.response.StudyScheduleCreateResponse;
import com.ssafy.ssabangpalbang.study.repository.ScheduleReminderRow;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyScheduleReminderWriter {

    private final StudyMemberRepository studyMemberRepository;
    private final MemberRepository memberRepository;
    private final StudyNotificationPort notificationPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriterResult sendForSchedule(ScheduleReminderRow row) {
        LinkedHashSet<Long> recipientIds = studyMemberRepository
                .findByStudyIdAndStatus(
                        row.getStudyId(),
                        StudyMemberStatus.ACTIVE
                ).stream()
                .map(StudyMember::getMemberId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        recipientIds.add(row.getLeaderId());

        Map<Long, Member> activeMembers = memberRepository.findAllById(recipientIds)
                .stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        int created = 0;
        int skipped = 0;
        for (Long recipientId : recipientIds) {
            Member member = activeMembers.get(recipientId);
            if (member == null) {
                continue;
            }
            try {
                boolean notificationCreated =
                        notificationPort.notifyScheduleReminder(
                                new StudyNotificationPort.ScheduleNotification(
                                        recipientId,
                                        null,
                                        row.getStudyId(),
                                        row.getScheduleId(),
                                        row.getStudyTitle(),
                                        StudyScheduleCreateResponse.f(row.getStartAt()),
                                        row.getMeetingPlace(),
                                        row.getStartAt().getEpochSecond(),
                                        member.isServiceNotificationAgreed()
                                )
                        );
                if (notificationCreated) {
                    created++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "임장 D-1 알림 생성에 실패했습니다. scheduleId={}, recipientId={}, reason=internal_error",
                        row.getScheduleId(),
                        recipientId,
                        exception
                );
            }
        }
        return new WriterResult(created, skipped);
    }

    public record WriterResult(
            int createdNotifications,
            int skippedDuplicates
    ) {
    }
}
