package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.PostUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import com.ssafy.ssabangpalbang.media.service.MediaFileSnapshot;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostUpdateServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T07:30:00Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-07-25T07:40:00Z");
    private static final Instant NOW =
            Instant.parse("2026-07-29T07:50:00Z");

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostAttachmentRepository postAttachmentRepository;
    @Mock
    private PostQueryRepository postQueryRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private MediaFileQueryPort mediaFileQueryPort;
    @Mock
    private PostResponseAssembler postResponseAssembler;
    @Mock
    private Clock clock;

    @InjectMocks
    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
    }

    @Test
    void updatesOnlyProvidedFieldAndTouchesSameValuePatch() {
        Post post = post(7L);
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("  기존 제목  ");
        PostUpdateResponse expected = stubSuccessfulResponse(post, List.of());

        PostUpdateResponse actual = postCommandService.update(
                7L,
                154L,
                request
        );

        assertThat(actual).isSameAs(expected);
        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
        assertThat(post.getBoardType()).isEqualTo(BoardType.FREE);
        assertThat(post.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
        verify(postRepository).flush();
        verifyNoInteractions(apartmentRepository);
        verify(mediaFileQueryPort).findAllById(List.of());
        verify(mediaFileQueryPort, never())
                .findAllByIdInForUpdate(anyList());
    }

    @Test
    void countsSupplementaryUnicodeByCodePoint() {
        Post post = post(7L);
        String title = "😀".repeat(200);
        PostUpdateRequest request = titleRequest(title);
        stubSuccessfulResponse(post, List.of());

        postCommandService.update(7L, 154L, request);

        assertThat(post.getTitle()).isEqualTo(title);
        assertThat(post.getTitle().codePointCount(
                0,
                post.getTitle().length()
        )).isEqualTo(200);
    }

    @Test
    void distinguishesApartmentRemovalFromOmission() {
        Post post = post(7L);
        PostUpdateRequest request = new PostUpdateRequest();
        request.setApartmentId(null);
        stubSuccessfulResponse(post, List.of());

        postCommandService.update(7L, 154L, request);

        assertThat(post.getApartmentId()).isNull();
        verifyNoInteractions(apartmentRepository);
    }

    @Test
    void rejectsEmptyAndInvalidPatchBeforePostLock() {
        assertError(
                () -> postCommandService.update(
                        7L,
                        154L,
                        new PostUpdateRequest()
                ),
                ErrorCode.POST_UPDATE_EMPTY
        );

        PostUpdateRequest nullTitle = new PostUpdateRequest();
        nullTitle.setTitle(null);
        assertError(
                () -> postCommandService.update(
                        7L,
                        154L,
                        nullTitle
                ),
                ErrorCode.INVALID_INPUT_VALUE
        );

        verify(postRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void appliesPermissionAndStatusChecksInRequiredOrder() {
        PostUpdateRequest request = titleRequest("수정");

        Post deleted = post(7L);
        ReflectionTestUtils.setField(deleted, "deletedAt", NOW);
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(deleted));
        assertError(
                () -> postCommandService.update(7L, 154L, request),
                ErrorCode.POST_NOT_FOUND
        );

        Post automatic = post(8L);
        ReflectionTestUtils.setField(automatic, "autoReport", true);
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(automatic));
        assertError(
                () -> postCommandService.update(7L, 154L, request),
                ErrorCode.POST_AUTO_REPORT_UPDATE_FORBIDDEN
        );

        Post other = post(8L);
        ReflectionTestUtils.setField(other, "status", PostStatus.HIDDEN);
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(other));
        assertError(
                () -> postCommandService.update(7L, 154L, request),
                ErrorCode.POST_UPDATE_FORBIDDEN
        );

        Post hidden = post(7L);
        ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(hidden));
        assertError(
                () -> postCommandService.update(7L, 154L, request),
                ErrorCode.POST_STATUS_CONFLICT
        );
    }

    @Test
    void replacesAttachmentsAfterDeleteAndFlushInRequestOrder() {
        Post post = post(7L);
        PostAttachment existing401 =
                PostAttachment.create(154L, 401L, 0);
        PostAttachment existing403 =
                PostAttachment.create(154L, 403L, 1);
        PostAttachment final402 =
                PostAttachment.create(154L, 402L, 0);
        PostAttachment final401 =
                PostAttachment.create(154L, 401L, 1);
        PostUpdateRequest request = new PostUpdateRequest();
        request.setFileIds(List.of(402L, 401L));

        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(
                        List.of(existing401, existing403),
                        List.of(final402, final401)
                );
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L, 402L)
        )).thenReturn(List.of(validFile(401L), validFile(402L)));
        when(postAttachmentRepository.findAttachedFileIds(
                List.of(402L, 401L)
        )).thenReturn(List.of(401L));
        stubResponseProjection(post);
        when(mediaFileQueryPort.findAllById(List.of(402L, 401L)))
                .thenReturn(List.of(validFile(402L), validFile(401L)));
        when(postResponseAssembler.assembleUpdatedPost(
                any(),
                eq(7L),
                eq(12L),
                any(),
                eq(List.of(final402, final401)),
                anyList()
        )).thenReturn(response());

        postCommandService.update(7L, 154L, request);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Iterable> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(postAttachmentRepository).saveAll(captor.capture());
        List<PostAttachment> saved = new ArrayList<>();
        captor.getValue().forEach(item ->
                saved.add((PostAttachment) item));
        assertThat(saved)
                .extracting(PostAttachment::getFileId)
                .containsExactly(402L, 401L);
        assertThat(saved)
                .extracting(PostAttachment::getDisplayOrder)
                .containsExactly(0, 1);
        InOrder order = inOrder(postAttachmentRepository);
        order.verify(postAttachmentRepository).deleteAllByPostId(154L);
        order.verify(postAttachmentRepository).flush();
        order.verify(postAttachmentRepository).saveAll(any());
        order.verify(postAttachmentRepository).flush();
    }

    @Test
    void removesAllAttachmentsForExplicitEmptyArray() {
        Post post = post(7L);
        PostAttachment existing =
                PostAttachment.create(154L, 401L, 0);
        PostUpdateRequest request = new PostUpdateRequest();
        request.setFileIds(List.of());

        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(List.of(existing), List.of());
        stubResponseProjection(post);
        when(mediaFileQueryPort.findAllById(List.of()))
                .thenReturn(List.of());
        when(postResponseAssembler.assembleUpdatedPost(
                any(),
                eq(7L),
                eq(12L),
                any(),
                eq(List.of()),
                eq(List.of())
        )).thenReturn(response());

        postCommandService.update(7L, 154L, request);

        verify(postAttachmentRepository).deleteAllByPostId(154L);
        verify(postAttachmentRepository, never()).saveAll(any());
        verify(mediaFileQueryPort, never())
                .findAllByIdInForUpdate(anyList());
    }

    @Test
    void retainsExistingAttachmentWithoutRevalidatingLifecycle() {
        Post post = post(7L);
        PostAttachment existing =
                PostAttachment.create(154L, 401L, 0);
        MediaFileSnapshot deletedFile = new MediaFileSnapshot(
                401L,
                8L,
                FileUsage.POST_ATTACHMENT,
                "old.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        PostUpdateRequest request = new PostUpdateRequest();
        request.setFileIds(List.of(401L));

        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(List.of(existing), List.of(existing));
        when(mediaFileQueryPort.findAllByIdInForUpdate(List.of(401L)))
                .thenReturn(List.of(deletedFile));
        when(postAttachmentRepository.findAttachedFileIds(List.of(401L)))
                .thenReturn(List.of(401L));
        stubResponseProjection(post);
        when(mediaFileQueryPort.findAllById(List.of(401L)))
                .thenReturn(List.of(deletedFile));
        when(postResponseAssembler.assembleUpdatedPost(
                any(),
                eq(7L),
                eq(12L),
                any(),
                eq(List.of(existing)),
                eq(List.of(deletedFile))
        )).thenReturn(response());

        postCommandService.update(7L, 154L, request);

        verify(postAttachmentRepository).deleteAllByPostId(154L);
        verify(postAttachmentRepository).saveAll(any());
    }

    @Test
    void rejectsOtherPostAttachmentBeforeApplyingAnyChange() {
        Post post = post(7L);
        PostAttachment existing =
                PostAttachment.create(154L, 401L, 0);
        PostUpdateRequest request = titleRequest("변경 제목");
        request.setFileIds(List.of(401L, 405L));

        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(List.of(existing));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L, 405L)
        )).thenReturn(List.of(validFile(401L), validFile(405L)));
        when(postAttachmentRepository.findAttachedFileIds(
                List.of(401L, 405L)
        )).thenReturn(List.of(401L, 405L));

        assertError(
                () -> postCommandService.update(7L, 154L, request),
                ErrorCode.POST_ATTACHMENT_ALREADY_USED
        );

        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getUpdatedAt()).isEqualTo(UPDATED_AT);
        verify(postAttachmentRepository, never()).deleteAllByPostId(any());
        verify(postRepository, never()).flush();
    }

    private PostUpdateResponse stubSuccessfulResponse(
            Post post,
            List<PostAttachment> attachments
    ) {
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(154L))
                .thenReturn(attachments);
        stubResponseProjection(post);
        when(mediaFileQueryPort.findAllById(anyList()))
                .thenReturn(List.of());
        PostUpdateResponse expected = response();
        when(postResponseAssembler.assembleUpdatedPost(
                any(),
                eq(7L),
                eq(12L),
                any(),
                eq(attachments),
                anyList()
        )).thenReturn(expected);
        return expected;
    }

    private void stubResponseProjection(Post post) {
        when(postQueryRepository.findVisibleDetail(154L))
                .thenReturn(Optional.of(row(post)));
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(new PostInteractionRow(
                        2L,
                        1L,
                        false,
                        null,
                        true,
                        1L
                )));
        when(postRepository.findViewCountById(154L))
                .thenReturn(Optional.of(12L));
    }

    private Post post(Long authorId) {
        Post post = Post.createMemberPost(
                authorId,
                BoardType.FREE,
                "기존 제목",
                "기존 본문",
                15L
        );
        ReflectionTestUtils.setField(post, "id", 154L);
        ReflectionTestUtils.setField(post, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(post, "updatedAt", UPDATED_AT);
        return post;
    }

    private PostDetailRow row(Post post) {
        return new PostDetailRow(
                154L,
                post.getBoardType(),
                post.getTitle(),
                post.getContent(),
                post.getStatus(),
                post.isAutoReport(),
                post.getAuthorId(),
                "집보는다람쥐",
                null,
                "JIPKONG",
                MemberStatus.ACTIVE,
                null,
                post.getApartmentId(),
                "래미안 옥수 리버젠",
                "서울특별시 성동구",
                null,
                null,
                CREATED_AT,
                NOW
        );
    }

    private Member activeMember() {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "deletedAt", null);
        return member;
    }

    private MediaFileSnapshot validFile(Long fileId) {
        return new MediaFileSnapshot(
                fileId,
                7L,
                FileUsage.POST_ATTACHMENT,
                "attachment-" + fileId + ".jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                null,
                null
        );
    }

    private PostUpdateRequest titleRequest(String title) {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle(title);
        return request;
    }

    private PostUpdateResponse response() {
        return new PostUpdateResponse(
                154L,
                "FREE",
                "기존 제목",
                "기존 본문",
                "ACTIVE",
                null,
                false,
                null,
                null,
                List.of(),
                12L,
                2L,
                1L,
                false,
                true,
                true,
                null,
                null
        );
    }

    private void assertError(
            ThrowingCallable callable,
            ErrorCode errorCode
    ) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
