package com.ssafy.ssabangpalbang.chat.event;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessagePushListenerTest {

    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock FcmTokenRepository fcmTokenRepository;
    @Mock FcmPushGateway fcmPushGateway;

    private ChatMessagePushListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatMessagePushListener(
                studyRepository,
                studyMemberRepository,
                memberRepository,
                fcmTokenRepository,
                fcmPushGateway
        );
    }

    @Test
    void 활성_동의_수신자에게만_보내고_한_기기_실패를_격리한다() {
        StudyMember sender = membership(42L, true);
        StudyMember agreed = membership(8L, true);
        StudyMember roomDisabled = membership(9L, false);
        StudyMember globallyDisabled = membership(10L, true);
        Member agreedMember = member(8L, true);
        Member globallyDisabledMember = member(10L, false);

        when(fcmPushGateway.isReady()).thenReturn(true);
        when(studyRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(study()));
        when(studyMemberRepository.findByStudyIdAndStatus(7L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(sender, agreed, roomDisabled, globallyDisabled));
        when(memberRepository.findAllById(any()))
                .thenReturn(List.of(agreedMember, globallyDisabledMember));
        when(fcmTokenRepository.findAllByMemberIdIn(any())).thenReturn(List.of(
                token(8L, "first-token", "first-device"),
                token(8L, "second-token", "second-device")
        ));
        doThrow(new IllegalStateException("provider failure"))
                .when(fcmPushGateway).sendNotification(
                        eq("first-token"), anyString(), anyString(), anyMap()
                );

        listener.sendAfterCommit(textEvent());

        verify(fcmPushGateway, times(2)).sendNotification(
                anyString(),
                eq("채팅 스터디"),
                eq("발신자: 반가워요"),
                eq(expectedData())
        );
        verify(fcmTokenRepository).findAllByMemberIdIn(eq(java.util.Set.of(8L)));
    }

    @Test
    void FCM이_준비되지_않으면_수신자를_조회하지_않는다() {
        when(fcmPushGateway.isReady()).thenReturn(false);

        listener.sendAfterCommit(textEvent());

        verifyNoInteractions(studyRepository, studyMemberRepository, memberRepository, fcmTokenRepository);
        verify(fcmPushGateway, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap()
        );
    }

    @Test
    void 이미지_메시지는_내용_대신_사진_안내를_보낸다() {
        StudyMember recipient = membership(8L, true);
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(studyRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(study()));
        when(studyMemberRepository.findByStudyIdAndStatus(
                7L,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(recipient));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(8L, true)));
        when(fcmTokenRepository.findAllByMemberIdIn(any()))
                .thenReturn(List.of(token(8L, "token", "device")));

        listener.sendAfterCommit(new ChatMessagePushRequestedEvent(
                1081L, 7L, 42L, "발신자", "IMAGE", null
        ));

        verify(fcmPushGateway).sendNotification(
                "token", "채팅 스터디", "발신자님이 사진을 보냈어요.",
                Map.of(
                        "notificationType", "STUDY_CHAT_MESSAGE",
                        "targetScreen", "STUDY_CHAT",
                        "studyId", "7",
                        "messageId", "1081"
                )
        );
    }

    @Test
    void 긴_텍스트의_이모지를_코드포인트_경계에서_자른다() {
        StudyMember recipient = membership(8L, true);
        when(fcmPushGateway.isReady()).thenReturn(true);
        when(studyRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(study()));
        when(studyMemberRepository.findByStudyIdAndStatus(7L, StudyMemberStatus.ACTIVE))
                .thenReturn(List.of(recipient));
        when(memberRepository.findAllById(any())).thenReturn(List.of(member(8L, true)));
        when(fcmTokenRepository.findAllByMemberIdIn(any()))
                .thenReturn(List.of(token(8L, "token", "device")));
        String content = "가".repeat(76) + "😀" + "나".repeat(10);

        listener.sendAfterCommit(new ChatMessagePushRequestedEvent(
                1082L, 7L, 42L, "발신자", "TEXT", content
        ));

        verify(fcmPushGateway).sendNotification(
                eq("token"),
                eq("채팅 스터디"),
                eq("발신자: " + "가".repeat(76) + "😀..."),
                anyMap()
        );
    }

    private ChatMessagePushRequestedEvent textEvent() {
        return new ChatMessagePushRequestedEvent(
                1080L, 7L, 42L, "발신자", "TEXT", "반가워요"
        );
    }

    private Study study() {
        Study study = Study.create(
                1L, 100L, "채팅 스터디", "소개", "목표", 5, StudyPurpose.STUDY
        );
        ReflectionTestUtils.setField(study, "id", 7L);
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        return study;
    }

    private StudyMember membership(Long memberId, boolean pushEnabled) {
        StudyMember membership = StudyMember.createMember(7L, memberId);
        membership.updateChatPushEnabled(pushEnabled);
        return membership;
    }

    private Member member(Long memberId, boolean serviceNotificationAgreed) {
        Member member = new Member(memberId + "@example.com", "hash", "회원" + memberId);
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        member.updateNotificationSettings(serviceNotificationAgreed, null);
        return member;
    }

    private FcmToken token(Long memberId, String token, String deviceId) {
        return FcmToken.of(
                memberId,
                token,
                deviceId,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
        );
    }

    private Map<String, String> expectedData() {
        return Map.of(
                "notificationType", "STUDY_CHAT_MESSAGE",
                "targetScreen", "STUDY_CHAT",
                "studyId", "7",
                "messageId", "1080"
        );
    }
}
