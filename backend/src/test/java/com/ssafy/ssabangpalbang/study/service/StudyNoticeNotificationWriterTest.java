package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationCommandRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import com.ssafy.ssabangpalbang.study.event.StudyNoticePushRequestedEvent;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyNoticeNotificationWriterTest {

    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationCommandRepository notificationCommandRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private StudyNoticeNotificationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new StudyNoticeNotificationWriter(
                studyMemberRepository,
                memberRepository,
                notificationCommandRepository,
                eventPublisher
        );
    }

    @Test
    void 작성자를_제외한_활성_멤버에게_알림을_저장하고_푸시를_요청한다() {
        Study study = study();
        StudyNotice notice = notice("준비물을 꼭 확인해 주세요.");
        Member agreed = member(8L, MemberStatus.ACTIVE, true);
        Member globallyDisabled = member(9L, MemberStatus.ACTIVE, false);
        Member withdrawn = member(10L, MemberStatus.WITHDRAWN, true);
        when(studyMemberRepository.findByStudyIdAndStatus(7L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(
                        StudyMember.createLeader(7L, 42L),
                        StudyMember.createMember(7L, 8L),
                        StudyMember.createMember(7L, 9L),
                        StudyMember.createMember(7L, 10L)
                ));
        when(memberRepository.findAllById(anyList()))
                .thenReturn(List.of(agreed, globallyDisabled, withdrawn));
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    return Optional.of(1000L + notification.getRecipientId());
                });

        writer.notifyCreated(study, notice, 42L);

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationCommandRepository, times(2))
                .insertIfAbsent(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getRecipientId)
                .containsExactly(8L, 9L);
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.getActorId()).isEqualTo(42L);
                    assertThat(notification.getType()).isEqualTo("STUDY_NOTICE_CREATED");
                    assertThat(notification.getTargetScreen()).isEqualTo("STUDY_DETAIL");
                    assertThat(notification.getTargetId()).isEqualTo(7L);
                    assertThat(notification.getTargetSubId()).isEqualTo(18L);
                    assertThat(notification.getBody()).contains("백그라운드 알림 스터디");
                });

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .containsExactly(
                        new StudyNoticePushRequestedEvent(
                                1008L, 8L, 7L, 18L,
                                "새 스터디 공지가 등록되었습니다.",
                                "'백그라운드 알림 스터디' 스터디: 준비물을 꼭 확인해 주세요.",
                                true
                        ),
                        new StudyNoticePushRequestedEvent(
                                1009L, 9L, 7L, 18L,
                                "새 스터디 공지가 등록되었습니다.",
                                "'백그라운드 알림 스터디' 스터디: 준비물을 꼭 확인해 주세요.",
                                false
                        )
                );
    }

    @Test
    void 동일한_공지_알림이_이미_있으면_푸시를_다시_요청하지_않는다() {
        when(studyMemberRepository.findByStudyIdAndStatus(7L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(StudyMember.createMember(7L, 8L)));
        when(memberRepository.findAllById(anyList()))
                .thenReturn(List.of(member(8L, MemberStatus.ACTIVE, true)));
        when(notificationCommandRepository.insertIfAbsent(any(Notification.class)))
                .thenReturn(Optional.empty());

        writer.notifyCreated(study(), notice("공지"), 42L);

        verify(eventPublisher, times(0)).publishEvent(any());
    }

    private Study study() {
        Study study = Study.create(
                1L, 42L, "백그라운드 알림 스터디", null, "목표", 6, null
        );
        ReflectionTestUtils.setField(study, "id", 7L);
        return study;
    }

    private StudyNotice notice(String content) {
        StudyNotice notice = StudyNotice.create(7L, content);
        ReflectionTestUtils.setField(notice, "id", 18L);
        return notice;
    }

    private Member member(Long memberId, MemberStatus status, boolean agreed) {
        Member member = new Member(memberId + "@example.com", "hash", "회원" + memberId);
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "status", status);
        member.updateNotificationSettings(agreed, null);
        return member;
    }
}
