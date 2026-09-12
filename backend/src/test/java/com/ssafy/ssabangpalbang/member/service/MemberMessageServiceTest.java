package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Follow;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberMessageSendRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberMessageSendResult;
import com.ssafy.ssabangpalbang.member.repository.FollowRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.notification.entity.Notification;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberMessageServiceTest {

    private static final Long SENDER_ID = 1L;
    private static final Long RECIPIENT_ID = 12L;
    private static final Long NOTIFICATION_ID = 81L;
    private static final String CLIENT_MESSAGE_ID =
            "8e70e108-7c81-477a-bb55-a9f334fb5e67";
    private static final String IDEMPOTENCY_KEY =
            "member-message:1:" + CLIENT_MESSAGE_ID;
    private static final OffsetDateTime SENT_AT = OffsetDateTime.of(
            2026,
            7,
            25,
            11,
            0,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberMessageWriter memberMessageWriter;

    private MemberMessageService memberMessageService;
    private Member sender;
    private Member recipient;

    @BeforeEach
    void setUp() {
        memberMessageService = new MemberMessageService(
                memberRepository,
                followRepository,
                notificationRepository,
                memberMessageWriter
        );
        sender = member(SENDER_ID, "sender@example.com", "보내는사람");
        recipient = member(
                RECIPIENT_ID,
                "recipient@example.com",
                "옥수탐방러"
        );
    }

    @Test
    void 팔로잉_사용자에게_쪽지를_보내면_알림을_한_건_저장한다() {
        Notification saved = notification("다음 임장도 같이 참여해요!");
        when(memberRepository.findById(SENDER_ID))
                .thenReturn(Optional.of(sender));
        when(notificationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(memberRepository.findById(RECIPIENT_ID))
                .thenReturn(Optional.of(recipient));
        when(followRepository.findByFollowerIdAndFollowingId(
                SENDER_ID,
                RECIPIENT_ID
        )).thenReturn(Optional.of(Follow.of(SENDER_ID, RECIPIENT_ID)));
        when(memberMessageWriter.save(
                eq(SENDER_ID),
                eq(RECIPIENT_ID),
                eq(true),
                eq("새로운 메시지가 도착했어요"),
                eq("다음 임장도 같이 참여해요!"),
                eq(IDEMPOTENCY_KEY),
                any(OffsetDateTime.class)
        )).thenReturn(saved);

        MemberMessageSendResult result = memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                new MemberMessageSendRequest(
                        "  다음 임장도 같이 참여해요!  ",
                        CLIENT_MESSAGE_ID
                )
        );

        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.MESSAGE_SENT);
        assertThat(result.created()).isTrue();
        assertThat(result.response().recipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(result.response().recipientNickname())
                .isEqualTo("옥수탐방러");
        assertThat(result.response().notificationId())
                .isEqualTo(NOTIFICATION_ID);
        assertThat(result.response().clientMessageId())
                .isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(result.response().sentAt()).isEqualTo(SENT_AT);
    }

    @Test
    void 같은_clientMessageId를_재전송하면_기존_결과를_반환한다() {
        Notification existing = notification("이미 저장된 쪽지");
        when(memberRepository.findById(SENDER_ID))
                .thenReturn(Optional.of(sender));
        when(notificationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findById(RECIPIENT_ID))
                .thenReturn(Optional.of(recipient));

        MemberMessageSendResult result = memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("재시도 내용")
        );

        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.MESSAGE_ALREADY_SENT);
        assertThat(result.created()).isFalse();
        assertThat(result.response().notificationId())
                .isEqualTo(NOTIFICATION_ID);
        verify(followRepository, never())
                .findByFollowerIdAndFollowingId(any(), any());
        verify(memberMessageWriter, never()).save(
                any(), any(), eq(true), any(), any(), any(), any()
        );
    }

    @Test
    void 동시_중복_저장은_유일성_제약_후_기존_결과로_복구한다() {
        Notification existing = notification("동시에 저장된 쪽지");
        when(memberRepository.findById(SENDER_ID))
                .thenReturn(Optional.of(sender));
        when(notificationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(memberRepository.findById(RECIPIENT_ID))
                .thenReturn(Optional.of(recipient));
        when(followRepository.findByFollowerIdAndFollowingId(
                SENDER_ID,
                RECIPIENT_ID
        )).thenReturn(Optional.of(Follow.of(SENDER_ID, RECIPIENT_ID)));
        when(memberMessageWriter.save(
                eq(SENDER_ID),
                eq(RECIPIENT_ID),
                eq(true),
                any(),
                any(),
                eq(IDEMPOTENCY_KEY),
                any(OffsetDateTime.class)
        )).thenThrow(new DataIntegrityViolationException("duplicate"));

        MemberMessageSendResult result = memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("동시 요청")
        );

        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.MESSAGE_ALREADY_SENT);
        assertThat(result.created()).isFalse();
        assertThat(result.response().notificationId())
                .isEqualTo(NOTIFICATION_ID);
    }

    @Test
    void 본인에게_보내면_명세의_400_오류를_던진다() {
        when(memberRepository.findById(SENDER_ID))
                .thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                SENDER_ID,
                request("본인 전송")
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.MEMBER_MESSAGE_SELF_NOT_ALLOWED
                        )
        );
    }

    @Test
    void 팔로잉_관계가_없으면_403_오류를_던진다() {
        prepareActiveMembers();
        when(followRepository.findByFollowerIdAndFollowingId(
                SENDER_ID,
                RECIPIENT_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("팔로잉 없음")
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.MEMBER_MESSAGE_FOLLOW_REQUIRED
                        )
        );
    }

    @Test
    void 탈퇴한_수신자는_존재를_숨기고_404를_던진다() {
        ReflectionTestUtils.setField(
                recipient,
                "status",
                MemberStatus.WITHDRAWN
        );
        prepareActiveMembers();

        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("탈퇴한 회원")
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
        );
        verify(followRepository, never())
                .findByFollowerIdAndFollowingId(any(), any());
    }

    @Test
    void 공백_쪽지는_필드_컨텍스트를_포함한_400을_던진다() {
        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("   ")
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    ErrorCode.MEMBER_MESSAGE_CONTENT_REQUIRED
                            );
                    assertThat(exception.getData())
                            .containsEntry("field", "content");
                }
        );
    }

    @Test
    void 공백_제거_후_500자를_초과하면_400을_던진다() {
        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                request("가".repeat(501))
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    ErrorCode.MEMBER_MESSAGE_CONTENT_TOO_LONG
                            );
                    assertThat(exception.getData())
                            .containsEntry("field", "content")
                            .containsEntry("maxLength", 500);
                }
        );
    }

    @Test
    void clientMessageId가_UUID가_아니면_400을_던진다() {
        assertThatThrownBy(() -> memberMessageService.send(
                SENDER_ID,
                RECIPIENT_ID,
                new MemberMessageSendRequest("내용", "not-a-uuid")
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(exception.getData())
                            .containsEntry("field", "clientMessageId")
                            .containsEntry(
                                    "reason",
                                    "올바른 UUID 형식이 아닙니다."
                            );
                }
        );
    }

    private void prepareActiveMembers() {
        when(memberRepository.findById(SENDER_ID))
                .thenReturn(Optional.of(sender));
        when(notificationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(memberRepository.findById(RECIPIENT_ID))
                .thenReturn(Optional.of(recipient));
    }

    private MemberMessageSendRequest request(String content) {
        return new MemberMessageSendRequest(content, CLIENT_MESSAGE_ID);
    }

    private Member member(Long id, String email, String nickname) {
        Member member = new Member(email, "password-hash", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Notification notification(String body) {
        Notification notification = Notification.message(
                RECIPIENT_ID,
                SENDER_ID,
                "새로운 메시지가 도착했어요",
                body,
                IDEMPOTENCY_KEY,
                SENT_AT
        );
        ReflectionTestUtils.setField(
                notification,
                "id",
                NOTIFICATION_ID
        );
        return notification;
    }
}
