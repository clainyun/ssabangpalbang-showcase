package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.domain.ChatReadStatus;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageDeleteResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageEditResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageListResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatNotificationSettingResponse;
import com.ssafy.ssabangpalbang.chat.event.ChatMessageBroadcastEvent;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatReadResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatUnreadCountResponse;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageHistoryRow;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import com.ssafy.ssabangpalbang.chat.repository.ChatReadStatusRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRestServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyMemberRepository studyMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatReadStatusRepository chatReadStatusRepository;
    @Mock
    private MediaPresignedUrlProvider mediaPresignedUrlProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ChatRestService chatRestService;

    @BeforeEach
    void setUp() {
        chatRestService = new ChatRestService(
                memberRepository, studyRepository, studyMemberRepository,
                chatMessageRepository, chatReadStatusRepository,
                mediaPresignedUrlProvider, eventPublisher
        );
        lenient().when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(activeMember()));
        lenient().when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        lenient().when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(studyMember(StudyMemberStatus.ACTIVE)));
    }

    @Test
    void 스터디_멤버가_아니면_이력_조회에서_CHAT_FORBIDDEN을_던진다() {
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRestService.getHistory(STUDY_ID, MEMBER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void size가_범위를_벗어나면_INVALID_INPUT_VALUE를_던진다() {
        assertThatThrownBy(() -> chatRestService.getHistory(STUDY_ID, MEMBER_ID, null, 51))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void 이력_조회는_TEXT_IMAGE_SYSTEM을_모두_포함하고_hasNext를_계산한다() {
        List<ChatMessageHistoryRow> rows = List.of(
                row(1080L, "TEXT", "다들 몇 시에 모일까요?", null, MEMBER_ID),
                row(1075L, "IMAGE", null, 91L, 51L),
                row(1074L, "SYSTEM", "일정이 변경되었습니다.", null, null)
        );
        when(chatMessageRepository.findHistoryPage(STUDY_ID, null, 31)).thenReturn(rows);
        when(mediaPresignedUrlProvider.isEnabled()).thenReturn(true);
        when(mediaPresignedUrlProvider.generateGetUrls(
                List.of(91L)
        )).thenReturn(Map.of(91L, "https://s3/presigned"));

        ChatMessageListResponse response = chatRestService.getHistory(STUDY_ID, MEMBER_ID, null, 30);

        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0).messageType()).isEqualTo("TEXT");
        assertThat(response.content().get(1).messageType()).isEqualTo("IMAGE");
        assertThat(response.content().get(1).image().imageUrl()).isEqualTo("https://s3/presigned");
        assertThat(response.content().get(2).messageType()).isEqualTo("SYSTEM");
        assertThat(response.content().get(2).sender()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void S3가_비활성화되면_이력의_IMAGE_URL을_발급하지_않는다() {
        List<ChatMessageHistoryRow> rows = List.of(
                row(1075L, "IMAGE", null, 91L, 51L)
        );
        when(chatMessageRepository.findHistoryPage(
                STUDY_ID,
                null,
                31
        )).thenReturn(rows);
        when(mediaPresignedUrlProvider.isEnabled())
                .thenReturn(false);

        ChatMessageListResponse response =
                chatRestService.getHistory(
                        STUDY_ID,
                        MEMBER_ID,
                        null,
                        30
                );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).image().imageUrl())
                .isNull();
        verify(mediaPresignedUrlProvider, never())
                .generateGetUrls(any());
    }

    @Test
    void 다음_페이지가_있으면_hasNext와_nextCursor를_반환한다() {
        List<ChatMessageHistoryRow> rows = List.of(
                row(1080L, "TEXT", "메시지1", null, MEMBER_ID),
                row(1079L, "TEXT", "메시지2", null, MEMBER_ID),
                row(1078L, "TEXT", "메시지3(다음 페이지 존재 판단용)", null, MEMBER_ID)
        );
        when(chatMessageRepository.findHistoryPage(STUDY_ID, null, 3)).thenReturn(rows);

        ChatMessageListResponse response = chatRestService.getHistory(STUDY_ID, MEMBER_ID, null, 2);

        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(1079L);
    }

    @Test
    void 읽음_상태가_없으면_upsert로_저장하고_실제_저장된_lastReadAt을_재조회해_반환한다() {
        // upsertLastReadAt에 전달된 lastReadAt(= Instant.now() 기준값)을 캡처해,
        // "upsert 직후 DB에 실제로 저장된 값을 재조회한다"는 계약을 그 캡처값으로 시뮬레이션한다.
        Instant[] capturedLastReadAt = new Instant[1];
        doAnswer(invocation -> {
            capturedLastReadAt[0] = invocation.getArgument(2);
            return null;
        }).when(chatReadStatusRepository).upsertLastReadAt(eq(STUDY_ID), eq(MEMBER_ID), any());
        when(chatReadStatusRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenAnswer(invocation -> Optional.of(
                        ChatReadStatus.create(STUDY_ID, MEMBER_ID, capturedLastReadAt[0])
                ));

        ChatReadResponse response = chatRestService.markRead(
                STUDY_ID, MEMBER_ID, new ChatReadRequest(null)
        );

        assertThat(response.studyId()).isEqualTo(STUDY_ID);
        assertThat(response.unreadCount()).isZero();
        assertThat(response.lastReadAt().toInstant()).isEqualTo(capturedLastReadAt[0]);
        verify(chatReadStatusRepository).upsertLastReadAt(eq(STUDY_ID), eq(MEMBER_ID), any());
    }

    @Test
    void lastReadMessageId가_해당_스터디_메시지가_아니면_CHAT_MESSAGE_INVALID를_던진다() {
        when(chatMessageRepository.findByIdAndStudyId(999L, STUDY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRestService.markRead(
                STUDY_ID, MEMBER_ID, new ChatReadRequest(999L)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_INVALID));

        verify(chatReadStatusRepository, never()).upsertLastReadAt(any(), any(), any());
    }

    @Test
    void 기존_읽음_상태가_있어도_lastReadMessageId_기준으로_upsert하고_재조회한_값을_반환한다() {
        Instant messageCreatedAt = Instant.parse("2026-07-22T04:40:00Z");
        ChatMessage message = ChatMessage.createText(STUDY_ID, MEMBER_ID, "content", null);
        ReflectionTestUtils.setField(message, "createdAt", messageCreatedAt);
        when(chatMessageRepository.findByIdAndStudyId(1080L, STUDY_ID)).thenReturn(Optional.of(message));
        when(chatReadStatusRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(
                        ChatReadStatus.create(STUDY_ID, MEMBER_ID, messageCreatedAt)
                ));

        ChatReadResponse response = chatRestService.markRead(
                STUDY_ID, MEMBER_ID, new ChatReadRequest(1080L)
        );

        assertThat(response.lastReadAt().toInstant()).isEqualTo(messageCreatedAt);
        verify(chatReadStatusRepository).upsertLastReadAt(STUDY_ID, MEMBER_ID, messageCreatedAt);
    }

    @Test
    void upsert_직후_재조회에_실패하면_IllegalStateException을_던진다() {
        when(chatReadStatusRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRestService.markRead(
                STUDY_ID, MEMBER_ID, new ChatReadRequest(null)
        )).isInstanceOf(IllegalStateException.class);

        verify(chatReadStatusRepository).upsertLastReadAt(eq(STUDY_ID), eq(MEMBER_ID), any());
    }

    @Test
    void 읽음_상태가_없으면_전체_메시지_수를_안읽은_수로_반환한다() {
        when(chatReadStatusRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.countUnreadExcludingSender(STUDY_ID, MEMBER_ID, null))
                .thenReturn(23L);

        ChatUnreadCountResponse response = chatRestService.getUnreadCount(STUDY_ID, MEMBER_ID);

        assertThat(response.unreadCount()).isEqualTo(23);
        assertThat(response.lastReadAt()).isNull();
        verify(chatMessageRepository).countUnreadExcludingSender(eq(STUDY_ID), eq(MEMBER_ID), eq(null));
    }

    @Test
    void 읽음_처리_이후_안읽은_수가_감소한다() {
        Instant lastReadAt = Instant.parse("2026-07-22T04:40:00Z");
        ChatReadStatus readStatus = ChatReadStatus.create(STUDY_ID, MEMBER_ID, lastReadAt);
        when(chatReadStatusRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(readStatus));
        when(chatMessageRepository.countUnreadExcludingSender(STUDY_ID, MEMBER_ID, null))
                .thenReturn(5L);
        when(chatMessageRepository.countUnreadExcludingSender(STUDY_ID, MEMBER_ID, lastReadAt))
                .thenReturn(0L);

        int beforeRead = chatRestService.getUnreadCount(STUDY_ID, MEMBER_ID).unreadCount();
        int afterRead = chatRestService.getUnreadCount(STUDY_ID, MEMBER_ID).unreadCount();

        assertThat(beforeRead).isEqualTo(5);
        assertThat(afterRead).isEqualTo(0);
    }

    @Test
    void 활성_스터디원은_채팅_푸시_설정을_조회할_수_있다() {
        StudyMember membership = studyMember(StudyMemberStatus.ACTIVE);
        membership.updateChatPushEnabled(false);
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));

        ChatNotificationSettingResponse response =
                chatRestService.getNotificationSetting(STUDY_ID, MEMBER_ID);

        assertThat(response.studyId()).isEqualTo(STUDY_ID);
        assertThat(response.pushEnabled()).isFalse();
    }

    @Test
    void 활성_스터디원은_채팅_푸시_설정을_변경할_수_있다() {
        StudyMember membership = studyMember(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership));

        ChatNotificationSettingResponse response =
                chatRestService.updateNotificationSetting(STUDY_ID, MEMBER_ID, false);

        assertThat(response.pushEnabled()).isFalse();
        assertThat(membership.isChatPushEnabled()).isFalse();
        verify(studyMemberRepository)
                .findForUpdateByStudyIdAndMemberId(STUDY_ID, MEMBER_ID);
    }

    @Test
    void 비활성_스터디원은_채팅_푸시_설정을_변경할_수_없다() {
        when(studyMemberRepository.findForUpdateByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(studyMember(StudyMemberStatus.REMOVED)));

        assertThatThrownBy(() ->
                chatRestService.updateNotificationSetting(STUDY_ID, MEMBER_ID, false))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void 본인_메시지를_삭제하면_deletedAt이_기록되고_삭제_브로드캐스트가_발행된다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, MEMBER_ID, "지울 내용", null);
        // createdAt은 @CreationTimestamp라 저장 시에만 채워진다. 브로드캐스트 페이로드가 읽으므로 채워 둔다.
        ReflectionTestUtils.setField(message, "createdAt", Instant.parse("2026-08-12T00:00:00Z"));
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        ChatMessageDeleteResponse response =
                chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L);

        assertThat(message.isDeleted()).isTrue();
        assertThat(response.studyId()).isEqualTo(STUDY_ID);
        assertThat(response.deletedAt()).isNotNull();

        ArgumentCaptor<ChatMessageBroadcastEvent> captor =
                ArgumentCaptor.forClass(ChatMessageBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().deleted()).isTrue();
        assertThat(captor.getValue().payload().content()).isNull();
        assertThat(captor.getValue().payload().image()).isNull();
    }

    @Test
    void 남의_메시지를_삭제하면_CHAT_MESSAGE_DELETE_FORBIDDEN을_던진다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, 999L, "남의 메시지", null);
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_DELETE_FORBIDDEN));
        assertThat(message.isDeleted()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void SYSTEM_메시지는_삭제할_수_없다() {
        ChatMessage message = ChatMessage.createSystem(STUDY_ID, "시스템 안내");
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_DELETE_FORBIDDEN));
    }

    @Test
    void 없는_메시지를_삭제하면_CHAT_MESSAGE_NOT_FOUND를_던진다() {
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    @Test
    void 완료된_스터디의_메시지는_삭제할_수_없다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study(StudyStatus.COMPLETED)));

        assertThatThrownBy(() -> chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_STUDY_COMPLETED));
    }

    @Test
    void 이미_삭제된_메시지를_다시_삭제하면_성공하되_재브로드캐스트하지_않는다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, MEMBER_ID, "지운 내용", null);
        Instant firstDeletedAt = Instant.parse("2026-08-12T01:00:00Z");
        message.markDeleted(firstDeletedAt);
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        ChatMessageDeleteResponse response =
                chatRestService.deleteMessage(STUDY_ID, MEMBER_ID, 11L);

        assertThat(response.deletedAt().toInstant()).isEqualTo(firstDeletedAt);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 삭제된_메시지는_이력에서_deleted_true이고_내용과_이미지를_숨긴다() {
        Instant deletedAt = Instant.parse("2026-08-12T01:00:00Z");
        when(chatMessageRepository.findHistoryPage(eq(STUDY_ID), any(), eq(31)))
                .thenReturn(List.of(
                        row(12L, "IMAGE", null, 77L, MEMBER_ID, deletedAt),
                        row(11L, "TEXT", "남은 메시지", null, MEMBER_ID)
                ));

        ChatMessageListResponse response =
                chatRestService.getHistory(STUDY_ID, MEMBER_ID, null, null);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).deleted()).isTrue();
        assertThat(response.content().get(0).content()).isNull();
        assertThat(response.content().get(0).image()).isNull();
        assertThat(response.content().get(1).deleted()).isFalse();
        assertThat(response.content().get(1).content()).isEqualTo("남은 메시지");
        // 삭제된 사진의 presigned URL은 발급 자체를 요청하지 않는다.
        verify(mediaPresignedUrlProvider, never()).generateGetUrls(any());
    }

    @Test
    void 본인_TEXT_메시지를_수정하면_본문과_editedAt이_바뀌고_수정_브로드캐스트가_발행된다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, MEMBER_ID, "원래 내용", null);
        ReflectionTestUtils.setField(message, "createdAt", Instant.parse("2026-08-12T00:00:00Z"));
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        ChatMessageEditResponse response = chatRestService.editMessage(
                STUDY_ID, MEMBER_ID, 11L, new ChatMessageEditRequest("고친 내용 ")
        );

        assertThat(message.getContent()).isEqualTo("고친 내용");
        assertThat(message.getEditedAt()).isNotNull();
        assertThat(response.content()).isEqualTo("고친 내용");
        assertThat(response.editedAt()).isNotNull();

        ArgumentCaptor<ChatMessageBroadcastEvent> captor =
                ArgumentCaptor.forClass(ChatMessageBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().deleted()).isFalse();
        assertThat(captor.getValue().payload().content()).isEqualTo("고친 내용");
        assertThat(captor.getValue().payload().editedAt()).isNotNull();
    }

    @Test
    void 남의_메시지를_수정하면_CHAT_MESSAGE_EDIT_FORBIDDEN을_던진다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, 999L, "남의 메시지", null);
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatRestService.editMessage(
                STUDY_ID, MEMBER_ID, 11L, new ChatMessageEditRequest("고침")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_EDIT_FORBIDDEN));
        assertThat(message.getContent()).isEqualTo("남의 메시지");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void IMAGE_메시지는_수정할_수_없다() {
        ChatMessage message = ChatMessage.createImage(STUDY_ID, MEMBER_ID, 77L, null);
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatRestService.editMessage(
                STUDY_ID, MEMBER_ID, 11L, new ChatMessageEditRequest("고침")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_TYPE_INVALID));
    }

    @Test
    void 삭제된_메시지를_수정하면_CHAT_MESSAGE_NOT_FOUND를_던진다() {
        ChatMessage message = ChatMessage.createText(STUDY_ID, MEMBER_ID, "지운 내용", null);
        message.markDeleted(Instant.parse("2026-08-12T01:00:00Z"));
        when(chatMessageRepository.findByIdAndStudyId(11L, STUDY_ID))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatRestService.editMessage(
                STUDY_ID, MEMBER_ID, 11L, new ChatMessageEditRequest("고침")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    private Member activeMember() {
        Member member = new Member("member@example.com", "hash", "루돌푸");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        return member;
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(1L, 100L, "title", "intro", "goal", 5, StudyPurpose.STUDY);
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyMember studyMember(StudyMemberStatus status) {
        StudyMember studyMember = StudyMember.createLeader(STUDY_ID, MEMBER_ID);
        ReflectionTestUtils.setField(studyMember, "status", status);
        return studyMember;
    }

    private ChatMessageHistoryRow row(
            Long messageId, String messageType, String content, Long imageFileId, Long senderId
    ) {
        return row(messageId, messageType, content, imageFileId, senderId, null);
    }

    private ChatMessageHistoryRow row(
            Long messageId, String messageType, String content, Long imageFileId, Long senderId,
            Instant deletedAt
    ) {
        return new ChatMessageHistoryRow() {
            @Override
            public Long getMessageId() {
                return messageId;
            }

            @Override
            public String getMessageType() {
                return messageType;
            }

            @Override
            public String getContent() {
                return content;
            }

            @Override
            public Long getImageFileId() {
                return imageFileId;
            }

            @Override
            public Long getSenderId() {
                return senderId;
            }

            @Override
            public String getSenderNickname() {
                return senderId == null ? null : "닉네임" + senderId;
            }

            @Override
            public String getSenderSelectedCharacterId() {
                return senderId == null ? null : "PALBANG";
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-07-22T04:40:00Z");
            }

            @Override
            public Instant getDeletedAt() {
                return deletedAt;
            }

            @Override
            public Instant getEditedAt() {
                return null;
            }
        };
    }
}
