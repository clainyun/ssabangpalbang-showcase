package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordRepository;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldRecordListServiceTest {

    private FieldVisitAccessService accessService;
    private FieldRecordRepository fieldRecordRepository;
    private MemberRepository memberRepository;
    private FieldRecordService service;

    @BeforeEach
    void setUp() {
        accessService = mock(FieldVisitAccessService.class);
        fieldRecordRepository = mock(FieldRecordRepository.class);
        memberRepository = mock(MemberRepository.class);
        MediaAccessUrlProvider urlProvider = mock(MediaAccessUrlProvider.class);
        when(urlProvider.issueAll(any())).thenReturn(Map.of());

        service = new FieldRecordService(
                accessService,
                mock(ChecklistItemRepository.class),
                fieldRecordRepository,
                mock(FieldRecordWriter.class),
                mock(FieldPhotoValidator.class),
                mock(FieldRecordCountRepository.class),
                mock(FileMetaRepository.class),
                urlProvider,
                memberRepository
        );

        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(session.getStatus()).thenReturn(FieldSessionStatus.IN_PROGRESS);
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.IN_PROGRESS);
        when(accessService.readRecordStatus(any(), any(), any()))
                .thenReturn(new FieldVisitReadStatus(session, participant, false));
    }

    @Test
    void mineOnly_false면_타인_기록도_조회하고_canEdit은_false다() {
        FieldRecord mine = textRecord(1L, 501L, 42L, "mine");
        FieldRecord other = textRecord(2L, 501L, 99L, "other");
        when(fieldRecordRepository.findPage(
                eq(100L), eq(42L), eq(false), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(List.of(mine, other));
        Member me = mockMember(42L, "me");
        Member otherMember = mockMember(99L, "other");
        when(memberRepository.findAllById(anyCollection()))
                .thenReturn(List.of(me, otherMember));

        var response = service.list(7L, 42L, null, null, false, null, 20);

        assertThat(response.content()).hasSize(2);
        var otherItem = response.content().stream()
                .filter(item -> item.sourceId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(otherItem.isMine()).isFalse();
        assertThat(otherItem.canEdit()).isFalse();
        assertThat(otherItem.canDelete()).isFalse();
    }

    @Test
    void 종료_후_조회는_허용하고_canEdit은_false다() {
        FieldSession session = mock(FieldSession.class);
        when(session.getId()).thenReturn(100L);
        when(session.getStatus()).thenReturn(FieldSessionStatus.ENDED);
        FieldParticipant participant = mock(FieldParticipant.class);
        when(participant.getStatus()).thenReturn(FieldParticipantStatus.ENDED);
        when(accessService.readRecordStatus(any(), any(), any()))
                .thenReturn(new FieldVisitReadStatus(session, participant, true));

        FieldRecord mine = textRecord(1L, 501L, 42L, "mine");
        when(fieldRecordRepository.findPage(
                eq(100L), eq(42L), eq(true), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(List.of(mine));
        Member me = mockMember(42L, "me");
        when(memberRepository.findAllById(anyCollection())).thenReturn(List.of(me));

        var response = service.list(7L, 42L, null, null, true, null, 20);

        assertThat(response.readOnly()).isTrue();
        assertThat(response.content().get(0).canEdit()).isFalse();
        assertThat(response.content().get(0).canDelete()).isFalse();
    }

    private static FieldRecord textRecord(Long id, Long itemId, Long authorId, String text) {
        FieldRecord record = FieldRecord.createText(
                100L, itemId, authorId, text, "11111111-1111-1111-1111-111111111111",
                "fp", Instant.now()
        );
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }

    private static Member mockMember(Long id, String nickname) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getNickname()).thenReturn(nickname);
        when(member.getSelectedCharacterId()).thenReturn("char-1");
        return member;
    }
}
