package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.PostCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.PostCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResult;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResult;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.response.PostResponseCode;
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
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T07:30:00Z");

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostAttachmentRepository postAttachmentRepository;
    @Mock
    private PostCommandRepository postCommandRepository;
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
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createsTrimmedMemberPostAndPreservesAttachmentOrder() {
        Member member = activeMember();
        Apartment apartment = apartment();
        MediaFileSnapshot file401 = validFile(401L);
        MediaFileSnapshot file402 = validFile(402L);
        PostCreateResponse expected = mock(PostCreateResponse.class);

        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(member));
        when(apartmentRepository.findById(15L))
                .thenReturn(Optional.of(apartment));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L, 402L)
        )).thenReturn(List.of(file401, file402));
        when(postAttachmentRepository.findAttachedFileIds(
                List.of(402L, 401L)
        )).thenReturn(List.of());
        stubSavedPost();
        when(postResponseAssembler.assembleCreatedPost(
                any(Post.class),
                eq(member),
                eq(apartment),
                eq(List.of(file402, file401))
        )).thenReturn(expected);

        PostCreateResponse actual = postCommandService.create(
                7L,
                request(
                        "INFORMATION",
                        "  제목  ",
                        "  본문  ",
                        15L,
                        List.of(402L, 401L)
                )
        );

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        assertThat(savedPost.getBoardType()).isEqualTo(BoardType.INFORMATION);
        assertThat(savedPost.getTitle()).isEqualTo("제목");
        assertThat(savedPost.getContent()).isEqualTo("본문");
        assertThat(savedPost.getStatus()).isEqualTo(PostStatus.ACTIVE);
        assertThat(savedPost.isAutoReport()).isFalse();
        assertThat(savedPost.getReportId()).isNull();
        assertThat(savedPost.getViewCount()).isZero();
        assertThat(savedPost.getAuthorId()).isEqualTo(7L);
        assertThat(savedPost.getApartmentId()).isEqualTo(15L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PostAttachment>> attachmentCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(postAttachmentRepository).saveAll(
                attachmentCaptor.capture()
        );
        List<PostAttachment> attachments = StreamSupport.stream(
                attachmentCaptor.getValue().spliterator(),
                false
        ).toList();
        assertThat(attachments)
                .extracting(PostAttachment::getFileId)
                .containsExactly(402L, 401L);
        assertThat(attachments)
                .extracting(PostAttachment::getDisplayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void createsFreePostWithoutApartmentOrAttachments() {
        Member member = activeMember();
        PostCreateResponse expected = mock(PostCreateResponse.class);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(member));
        stubSavedPost();
        when(postResponseAssembler.assembleCreatedPost(
                any(Post.class),
                eq(member),
                eq(null),
                eq(List.of())
        )).thenReturn(expected);

        PostCreateResponse actual = postCommandService.create(
                7L,
                request("FREE", "제목", "본문", null, null)
        );

        assertThat(actual).isSameAs(expected);
        verify(postRepository).flush();
        verifyNoInteractions(apartmentRepository);
        verifyNoInteractions(mediaFileQueryPort);
        verify(postAttachmentRepository, never())
                .saveAll(any());
    }

    @Test
    void rejectsInactiveOrDeletedMemberBeforeOtherValidation() {
        Member inactive = activeMember();
        ReflectionTestUtils.setField(
                inactive,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(inactive));

        assertError(
                () -> postCommandService.create(
                        7L,
                        request("INFO", "제목", "본문", null, null)
                ),
                ErrorCode.MEMBER_NOT_FOUND
        );
        verifyNoInteractions(apartmentRepository);
        verifyNoInteractions(postRepository);
    }

    @Test
    void rejectsInvalidBoardTypeBeforeApartmentLookup() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));

        assertError(
                () -> postCommandService.create(
                        7L,
                        request("INFO", "제목", "본문", 15L, null)
                ),
                ErrorCode.POST_BOARD_TYPE_INVALID
        );
        verifyNoInteractions(apartmentRepository);
        verifyNoInteractions(postRepository);
    }

    @Test
    void rejectsBlankAndOverlongText() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));

        for (String invalidTitle : List.of(
                " ",
                "\u00A0",
                "\u200B",
                "제\u0000목"
        )) {
            assertError(
                    () -> postCommandService.create(
                            7L,
                            request(
                                    "FREE",
                                    invalidTitle,
                                    "본문",
                                    null,
                                    null
                            )
                    ),
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
        assertError(
                () -> postCommandService.create(
                        7L,
                        request(
                                "FREE",
                                "제목",
                                "가".repeat(5001),
                                null,
                                null
                        )
                ),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verifyNoInteractions(postRepository);
    }

    @Test
    void rejectsMissingApartment() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(apartmentRepository.findById(15L))
                .thenReturn(Optional.empty());

        assertError(
                () -> postCommandService.create(
                        7L,
                        request("FREE", "제목", "본문", 15L, null)
                ),
                ErrorCode.APARTMENT_NOT_FOUND
        );
        verifyNoInteractions(postRepository);
    }

    @Test
    void rejectsDuplicateFileIdsBeforeLocking() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));

        assertError(
                () -> postCommandService.create(
                        7L,
                        request(
                                "FREE",
                                "제목",
                                "본문",
                                null,
                                List.of(401L, 401L)
                        )
                ),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verifyNoInteractions(mediaFileQueryPort);
        verifyNoInteractions(postRepository);
    }

    @Test
    void reportsFirstMissingFileInSortedLockOrder() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L, 402L)
        )).thenReturn(List.of(validFile(402L)));

        assertThatThrownBy(() -> postCommandService.create(
                7L,
                request(
                        "FREE",
                        "제목",
                        "본문",
                        null,
                        List.of(402L, 401L)
                )
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.MEDIA_FILE_NOT_FOUND);
                    assertThat(exception.getData())
                            .containsEntry("fileId", 401L);
                }
        );
        verifyNoInteractions(postRepository);
    }

    @ParameterizedTest
    @MethodSource("inaccessibleFiles")
    void rejectsInaccessibleFiles(MediaFileSnapshot file) {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L)
        )).thenReturn(List.of(file));

        assertError(
                () -> postCommandService.create(
                        7L,
                        request(
                                "FREE",
                                "제목",
                                "본문",
                                null,
                                List.of(401L)
                        )
                ),
                ErrorCode.MEDIA_ACCESS_DENIED
        );
        verifyNoInteractions(postRepository);
    }

    @Test
    void rejectsFileAlreadyAttachedToAnotherPost() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L)
        )).thenReturn(List.of(validFile(401L)));
        when(postAttachmentRepository.findAttachedFileIds(
                List.of(401L)
        )).thenReturn(List.of(401L));

        assertError(
                () -> postCommandService.create(
                        7L,
                        request(
                                "FREE",
                                "제목",
                                "본문",
                                null,
                                List.of(401L)
                        )
                ),
                ErrorCode.POST_ATTACHMENT_ALREADY_USED
        );
        verifyNoInteractions(postRepository);
    }

    @Test
    void mapsOnlyAttachmentFileUniqueConstraintToConflict() {
        stubValidAttachmentCreation();
        doThrow(dataIntegrityViolation("uq_post_attachment_file"))
                .when(postAttachmentRepository)
                .flush();

        assertError(
                () -> postCommandService.create(
                        7L,
                        request(
                                "FREE",
                                "제목",
                                "본문",
                                null,
                                List.of(401L)
                        )
                ),
                ErrorCode.POST_ATTACHMENT_ALREADY_USED
        );
        verify(postResponseAssembler, never())
                .assembleCreatedPost(any(), any(), any(), any());
    }

    @Test
    void doesNotMisclassifyOtherConstraintViolations() {
        stubValidAttachmentCreation();
        doThrow(dataIntegrityViolation(
                "post_attachment_post_id_display_order_key"
        )).when(postAttachmentRepository).flush();

        assertThatThrownBy(() -> postCommandService.create(
                7L,
                request(
                        "FREE",
                        "제목",
                        "본문",
                        null,
                        List.of(401L)
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registersLikeAndReturnsCurrentInteractions() {
        Post post = activePost();
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForShare(154L))
                .thenReturn(Optional.of(post));
        when(postCommandRepository.insertLikeIfAbsent(154L, 7L))
                .thenReturn(Optional.of(NOW));
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(new PostInteractionRow(
                        3L,
                        2L,
                        true,
                        new BigDecimal("22.70"),
                        true,
                        8L
                )));

        PostLikeResult result = postCommandService.like(7L, 154L);

        assertThat(result.responseCode())
                .isEqualTo(PostResponseCode.POST_LIKE_SUCCESS);
        assertThat(result.response().postId()).isEqualTo(154L);
        assertThat(result.response().likedByMe()).isTrue();
        assertThat(result.response().likeCount()).isEqualTo(3L);
        assertThat(result.response().commentCount()).isEqualTo(2L);
        assertThat(result.response().viewCount()).isEqualTo(11L);
        assertThat(result.response().isHot()).isTrue();
        assertThat(result.response().hotRank()).isEqualTo(8L);
        assertThat(result.response().likedAt().toInstant()).isEqualTo(NOW);
        verify(postCommandRepository, never())
                .findLikedAt(any(), any());
    }

    @Test
    void repeatedLikeKeepsOriginalTimestampAndReturnsAlreadyExists() {
        Instant firstLikedAt = NOW.minusSeconds(30);
        Post post = activePost();
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForShare(154L))
                .thenReturn(Optional.of(post));
        when(postCommandRepository.insertLikeIfAbsent(154L, 7L))
                .thenReturn(Optional.empty());
        when(postCommandRepository.findLikedAt(154L, 7L))
                .thenReturn(Optional.of(firstLikedAt));
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(new PostInteractionRow(
                        1L,
                        0L,
                        true,
                        new BigDecimal("17.70"),
                        false,
                        12L
                )));

        PostLikeResult result = postCommandService.like(7L, 154L);

        assertThat(result.responseCode())
                .isEqualTo(PostResponseCode.POST_LIKE_ALREADY_EXISTS);
        assertThat(result.response().likedAt().toInstant())
                .isEqualTo(firstLikedAt);
        assertThat(result.response().isHot()).isFalse();
        assertThat(result.response().hotScore())
                .isEqualByComparingTo("17.70");
        assertThat(result.response().hotRank()).isNull();
    }

    @Test
    void rejectsHiddenPostBeforeWritingLike() {
        Post post = activePost();
        ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForShare(154L))
                .thenReturn(Optional.of(post));

        assertError(
                () -> postCommandService.like(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );

        verifyNoInteractions(postCommandRepository, postQueryRepository);
    }

    @Test
    void rejectsDeletedPostBeforeWritingLike() {
        Post post = activePost();
        ReflectionTestUtils.setField(post, "deletedAt", NOW);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForShare(154L))
                .thenReturn(Optional.of(post));

        assertError(
                () -> postCommandService.like(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );

        verifyNoInteractions(postCommandRepository, postQueryRepository);
    }

    @Test
    void rejectsInactiveMemberBeforeLoadingPostForLike() {
        Member inactive = activeMember();
        ReflectionTestUtils.setField(
                inactive,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(inactive));

        assertError(
                () -> postCommandService.like(7L, 154L),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verifyNoInteractions(
                postRepository,
                postCommandRepository,
                postQueryRepository
        );
    }

    @Test
    void removesLikeFromAutoReportAndReturnsCurrentInteractions() {
        Post post = activePost();
        ReflectionTestUtils.setField(post, "autoReport", true);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postCommandRepository.deleteLike(154L, 7L))
                .thenReturn(1);
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(new PostInteractionRow(
                        0L,
                        2L,
                        false,
                        new BigDecimal("17.70"),
                        false,
                        12L
                )));

        PostUnlikeResult result = postCommandService.unlike(7L, 154L);

        assertThat(result.responseCode())
                .isEqualTo(PostResponseCode.POST_UNLIKE_SUCCESS);
        assertThat(result.response().postId()).isEqualTo(154L);
        assertThat(result.response().likedByMe()).isFalse();
        assertThat(result.response().likeCount()).isZero();
        assertThat(result.response().commentCount()).isEqualTo(2L);
        assertThat(result.response().viewCount()).isEqualTo(11L);
        assertThat(result.response().isHot()).isFalse();
        assertThat(result.response().hotScore())
                .isEqualByComparingTo("17.70");
        assertThat(result.response().hotRank()).isNull();
        assertThat(result.response().unlikedAt().toInstant())
                .isEqualTo(NOW);
        verify(postCommandRepository).deleteLike(154L, 7L);
    }

    @Test
    void repeatedUnlikeReturnsAlreadyUnlikedWithCurrentTimestamp() {
        Post post = activePost();
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));
        when(postCommandRepository.deleteLike(154L, 7L))
                .thenReturn(0);
        when(postQueryRepository.findInteractions(154L, 7L))
                .thenReturn(Optional.of(new PostInteractionRow(
                        0L,
                        0L,
                        false,
                        null,
                        null,
                        null
                )));

        PostUnlikeResult result = postCommandService.unlike(7L, 154L);

        assertThat(result.responseCode())
                .isEqualTo(PostResponseCode.POST_ALREADY_UNLIKED);
        assertThat(result.response().likedByMe()).isFalse();
        assertThat(result.response().likeCount()).isZero();
        assertThat(result.response().isHot()).isFalse();
        assertThat(result.response().hotScore()).isNull();
        assertThat(result.response().hotRank()).isNull();
        assertThat(result.response().unlikedAt().toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void rejectsHiddenPostBeforeDeletingLike() {
        Post post = activePost();
        ReflectionTestUtils.setField(post, "status", PostStatus.HIDDEN);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));

        assertError(
                () -> postCommandService.unlike(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );

        verifyNoInteractions(postCommandRepository, postQueryRepository);
    }

    @Test
    void rejectsDeletedPostBeforeDeletingLike() {
        Post post = activePost();
        ReflectionTestUtils.setField(post, "deletedAt", NOW);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(activeMember()));
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));

        assertError(
                () -> postCommandService.unlike(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );

        verifyNoInteractions(postCommandRepository, postQueryRepository);
    }

    @Test
    void rejectsInactiveMemberBeforeLoadingPostForUnlike() {
        Member inactive = activeMember();
        ReflectionTestUtils.setField(
                inactive,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(inactive));

        assertError(
                () -> postCommandService.unlike(7L, 154L),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verifyNoInteractions(
                postRepository,
                postCommandRepository,
                postQueryRepository
        );
    }

    private static Stream<MediaFileSnapshot> inaccessibleFiles() {
        return Stream.of(
                file(
                        401L, 8L, FileUsage.POST_ATTACHMENT,
                        UploadStatus.COMPLETED, null, null
                ),
                file(
                        401L, 7L, FileUsage.CHAT_IMAGE,
                        UploadStatus.COMPLETED, null, null
                ),
                file(
                        401L, 7L, FileUsage.POST_ATTACHMENT,
                        UploadStatus.PENDING, null, null
                ),
                file(
                        401L, 7L, FileUsage.POST_ATTACHMENT,
                        UploadStatus.COMPLETED, NOW, NOW.minusSeconds(1)
                ),
                file(
                        401L, 7L, FileUsage.POST_ATTACHMENT,
                        UploadStatus.COMPLETED, NOW, null
                )
        );
    }

    private void stubValidAttachmentCreation() {
        Member member = activeMember();
        MediaFileSnapshot file = validFile(401L);
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.of(member));
        when(mediaFileQueryPort.findAllByIdInForUpdate(
                List.of(401L)
        )).thenReturn(List.of(file));
        when(postAttachmentRepository.findAttachedFileIds(
                List.of(401L)
        )).thenReturn(List.of());
        stubSavedPost();
    }

    private void stubSavedPost() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> {
                    Post post = invocation.getArgument(0);
                    ReflectionTestUtils.setField(post, "id", 154L);
                    return post;
                });
    }

    private Post activePost() {
        Post post = BeanUtils.instantiateClass(Post.class);
        ReflectionTestUtils.setField(post, "id", 154L);
        ReflectionTestUtils.setField(post, "status", PostStatus.ACTIVE);
        ReflectionTestUtils.setField(post, "deletedAt", null);
        ReflectionTestUtils.setField(post, "viewCount", 11L);
        return post;
    }

    private DataIntegrityViolationException dataIntegrityViolation(
            String constraintName
    ) {
        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "constraint violation",
                        new SQLException("duplicate"),
                        constraintName
                );
        return new DataIntegrityViolationException(
                "could not execute statement",
                cause
        );
    }

    private PostCreateRequest request(
            String boardType,
            String title,
            String content,
            Long apartmentId,
            List<Long> fileIds
    ) {
        return new PostCreateRequest(
                boardType,
                title,
                content,
                apartmentId,
                fileIds
        );
    }

    private Member activeMember() {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "nickname", "집보는다람쥐");
        ReflectionTestUtils.setField(
                member,
                "selectedCharacterId",
                "JIPKONG"
        );
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.ACTIVE
        );
        ReflectionTestUtils.setField(member, "deletedAt", null);
        return member;
    }

    private Apartment apartment() {
        Apartment apartment = BeanUtils.instantiateClass(Apartment.class);
        ReflectionTestUtils.setField(apartment, "id", 15L);
        ReflectionTestUtils.setField(
                apartment,
                "name",
                "래미안 옥수 리버젠"
        );
        return apartment;
    }

    private MediaFileSnapshot validFile(Long fileId) {
        return file(
                fileId,
                7L,
                FileUsage.POST_ATTACHMENT,
                UploadStatus.COMPLETED,
                null,
                null
        );
    }

    private static MediaFileSnapshot file(
            Long fileId,
            Long ownerId,
            FileUsage usage,
            UploadStatus status,
            Instant expiresAt,
            Instant deletedAt
    ) {
        return new MediaFileSnapshot(
                fileId,
                ownerId,
                usage,
                "attachment-" + fileId + ".jpg",
                "image/jpeg",
                status,
                expiresAt,
                deletedAt
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
