package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.response.PostDeleteResponse;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDeleteServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T07:30:00Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-07-25T07:40:00Z");
    private static final Instant NOW =
            Instant.parse("2026-07-29T08:00:00Z");

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
    void softDeletesOwnedActivePostAndReturnsMinimalSeoulResponse() {
        Post post = post(7L);
        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(Optional.of(post));

        PostDeleteResponse response =
                postCommandService.delete(7L, 154L);

        assertThat(response.postId()).isEqualTo(154L);
        assertThat(response.deletedAt().toInstant()).isEqualTo(NOW);
        assertThat(response.deletedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(post.getDeletedAt()).isEqualTo(NOW);
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
        assertThat(post.getStatus()).isEqualTo(PostStatus.ACTIVE);
        assertThat(post.getTitle()).isEqualTo("기존 제목");
        assertThat(post.getContent()).isEqualTo("기존 본문");
        assertThat(post.getApartmentId()).isEqualTo(15L);
        assertThat(post.getReportId()).isNull();
        assertThat(post.getViewCount()).isZero();
        verify(postRepository).flush();
        verifyNoInteractions(
                postAttachmentRepository,
                postQueryRepository,
                apartmentRepository,
                mediaFileQueryPort,
                postResponseAssembler
        );
    }

    @Test
    void appliesDeletePermissionAndStatusChecksInRequiredOrder() {
        Post deleted = post(8L);
        ReflectionTestUtils.setField(deleted, "deletedAt", UPDATED_AT);
        ReflectionTestUtils.setField(deleted, "autoReport", true);
        ReflectionTestUtils.setField(deleted, "status", PostStatus.HIDDEN);

        Post automatic = post(8L);
        ReflectionTestUtils.setField(automatic, "autoReport", true);
        ReflectionTestUtils.setField(automatic, "status", PostStatus.HIDDEN);

        Post other = post(8L);
        ReflectionTestUtils.setField(other, "status", PostStatus.HIDDEN);

        Post hidden = post(7L);
        ReflectionTestUtils.setField(hidden, "status", PostStatus.HIDDEN);

        when(postRepository.findByIdForUpdate(154L))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(deleted),
                        Optional.of(automatic),
                        Optional.of(other),
                        Optional.of(hidden)
                );

        assertError(
                () -> postCommandService.delete(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );
        assertError(
                () -> postCommandService.delete(7L, 154L),
                ErrorCode.POST_NOT_FOUND
        );
        assertError(
                () -> postCommandService.delete(7L, 154L),
                ErrorCode.POST_AUTO_REPORT_DELETE_FORBIDDEN
        );
        assertError(
                () -> postCommandService.delete(7L, 154L),
                ErrorCode.POST_DELETE_FORBIDDEN
        );

        assertThatThrownBy(() -> postCommandService.delete(7L, 154L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.POST_STATUS_CONFLICT
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "현재 상태에서는 게시글을 "
                                                    + "삭제할 수 없습니다."
                                    );
                            assertThat(exception.getData())
                                    .isEqualTo(Map.of(
                                            "status",
                                            "HIDDEN"
                                    ));
                        }
                );

        verify(postRepository, never()).flush();
    }

    @Test
    void rejectsInactiveMemberBeforeLockingPost() {
        when(memberRepository.findByIdForShare(7L))
                .thenReturn(Optional.empty());

        assertError(
                () -> postCommandService.delete(7L, 154L),
                ErrorCode.MEMBER_NOT_FOUND
        );

        verify(postRepository, never()).findByIdForUpdate(any());
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

    private Member activeMember() {
        Member member = BeanUtils.instantiateClass(Member.class);
        ReflectionTestUtils.setField(member, "id", 7L);
        ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(member, "deletedAt", null);
        return member;
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
