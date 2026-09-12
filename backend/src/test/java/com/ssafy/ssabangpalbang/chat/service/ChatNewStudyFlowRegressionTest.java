package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageBroadcastResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageListResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.event.ChatMessageBroadcastEvent;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageHistoryRow;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import com.ssafy.ssabangpalbang.chat.repository.ChatReadStatusRepository;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.StudyMembershipPortImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 새 스터디 생성 직후 생성자 채팅 권한·저장·B REST 이력 조회 회귀를
 * 기존 단위 테스트 인프라로 검증한다.
 *
 * <p>실제 WebSocket 세션 통합은 ChatWebSocketConfigTest 범위(CONNECT)에 두고,
 * 이 클래스는 저장·권한·REST 조회가 B 입장으로 재발행되지 않음을 확인한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ChatNewStudyFlowRegressionTest {

    private static final Long STUDY_ID = 10L;
    private static final Long LEADER_ID = 7L;
    private static final Long MEMBER_B_ID = 8L;
    private static final Long MESSAGE_ID = 1080L;
    private static final String CLIENT_MESSAGE_ID = "client-a-1";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyMemberRepository studyMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;
    @Mock
    private MediaPresignedUrlProvider mediaPresignedUrlProvider;

    private StudyMembershipPortImpl studyMembershipPort;
    private ChatMessageWriter chatMessageWriter;
    private ChatRestService chatRestService;

    @BeforeEach
    void setUp() {
        studyMembershipPort = new StudyMembershipPortImpl(studyRepository, studyMemberRepository);
        chatMessageWriter = new ChatMessageWriter(chatMessageRepository, eventPublisher);
        chatRestService = new ChatRestService(
                memberRepository,
                studyRepository,
                studyMemberRepository,
                chatMessageRepository,
                chatReadStatusRepository,
                mediaPresignedUrlProvider,
                eventPublisher
        );
    }

    @Test
    void 새_스터디_생성자_ACTIVE_LEADER는_즉시_채팅_멤버이고_RECRUITING에서_SEND_가능하다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(recruitingStudy()));
        StudyMember leader = StudyMember.createLeader(STUDY_ID, LEADER_ID);
        assertThat(leader.getRole()).isEqualTo(StudyMemberRole.LEADER);
        assertThat(leader.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, LEADER_ID))
                .thenReturn(Optional.of(leader));

        assertThat(studyMembershipPort.isStudyMember(STUDY_ID, LEADER_ID)).isTrue();
        assertThat(studyMembershipPort.isStudyOpenForSend(STUDY_ID)).isTrue();
    }

    @Test
    void 생성자_TEXT_저장_성공_시_Broadcast_Event가_한_번_발행된다() {
        ChatMessage saved = ChatMessage.createText(
                STUDY_ID, LEADER_ID, "안녕하세요", CLIENT_MESSAGE_ID
        );
        ReflectionTestUtils.setField(saved, "id", MESSAGE_ID);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-08-06T01:00:00Z"));
        when(chatMessageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = chatMessageWriter.saveText(
                STUDY_ID,
                LEADER_ID,
                "안녕하세요",
                CLIENT_MESSAGE_ID,
                new ChatSenderResponse(LEADER_ID, "리더", "PALBANG")
        );

        assertThat(result.getId()).isEqualTo(MESSAGE_ID);
        ArgumentCaptor<ChatMessageBroadcastEvent> captor =
                ArgumentCaptor.forClass(ChatMessageBroadcastEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ChatMessageBroadcastResponse payload = captor.getValue().payload();
        assertThat(payload.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(payload.studyId()).isEqualTo(STUDY_ID);
        assertThat(payload.messageType()).isEqualTo("TEXT");
    }

    @Test
    void B의_REST_이력_조회는_기존_A_메시지를_반환하고_저장을_다시_호출하지_않는다() {
        when(memberRepository.findById(MEMBER_B_ID)).thenReturn(Optional.of(activeMember(MEMBER_B_ID)));
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(recruitingStudy()));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_B_ID))
                .thenReturn(Optional.of(StudyMember.createMember(STUDY_ID, MEMBER_B_ID)));
        when(chatMessageRepository.findHistoryPage(eq(STUDY_ID), eq(null), eq(31)))
                .thenReturn(List.of(historyRow(MESSAGE_ID, LEADER_ID, "안녕하세요")));

        ChatMessageListResponse response =
                chatRestService.getHistory(STUDY_ID, MEMBER_B_ID, null, 30);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).messageId()).isEqualTo(MESSAGE_ID);
        assertThat(response.content().get(0).sender().memberId()).isEqualTo(LEADER_ID);
        verify(chatMessageRepository, never()).save(any());
        verify(chatMessageRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private Study recruitingStudy() {
        Study study = Study.create(
                15L, LEADER_ID, "새 스터디", "intro", "goal", 4, StudyPurpose.STUDY
        );
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        ReflectionTestUtils.setField(study, "status", StudyStatus.RECRUITING);
        return study;
    }

    private Member activeMember(Long memberId) {
        Member member = new Member("member@example.com", "hash", "멤버");
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        return member;
    }

    private ChatMessageHistoryRow historyRow(Long messageId, Long senderId, String content) {
        return new ChatMessageHistoryRow() {
            @Override
            public Long getMessageId() {
                return messageId;
            }

            @Override
            public String getMessageType() {
                return "TEXT";
            }

            @Override
            public String getContent() {
                return content;
            }

            @Override
            public Long getImageFileId() {
                return null;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-08-06T01:00:00Z");
            }

            @Override
            public Instant getDeletedAt() {
                return null;
            }

            @Override
            public Instant getEditedAt() {
                return null;
            }

            @Override
            public Long getSenderId() {
                return senderId;
            }

            @Override
            public String getSenderNickname() {
                return "리더";
            }

            @Override
            public String getSenderSelectedCharacterId() {
                return "PALBANG";
            }
        };
    }
}
