package com.ssafy.ssabangpalbang.community.support;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.response.PostDetailResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostListItemResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListCardRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostListKeyRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostReactionCountRow;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.media.service.MediaFileSnapshot;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostResponseAssemblerTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T07:40:00Z");

    @Mock
    private MediaAccessUrlProvider mediaAccessUrlProvider;

    private PostResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PostResponseAssembler(
                mediaAccessUrlProvider,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PostContentPreviewer()
        );
    }

    @Test
    void assemblesListCardWithOneBatchedAvailableThumbnail() {
        PostAttachment unavailable =
                PostAttachment.create(154L, 400L, 0);
        PostAttachment available =
                PostAttachment.create(154L, 401L, 1);
        MediaFileSnapshot pending = new MediaFileSnapshot(
                400L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "pending.jpg",
                "image/jpeg",
                UploadStatus.PENDING,
                null,
                null
        );
        MediaFileSnapshot image = new MediaFileSnapshot(
                401L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "station-route.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                NOW.plusSeconds(600),
                null
        );
        when(mediaAccessUrlProvider.issueAll(argThat(fileIds ->
                fileIds.size() == 1 && fileIds.contains(401L))))
                .thenReturn(Map.of(
                        401L,
                        new MediaAccessUrl(
                                "https://example.com/401",
                                NOW.plusSeconds(300)
                        )
                ));
        Instant createdAt = Instant.parse("2026-07-29T07:30:00Z");
        PostListCardRow row = new PostListCardRow(
                154L,
                BoardType.INFORMATION,
                "제목",
                "  첫 줄\r\n둘째 줄  ",
                false,
                7L,
                "집보는다람쥐",
                null,
                "JIPKONG",
                MemberStatus.ACTIVE,
                null,
                15L,
                "래미안 옥수 리버젠",
                null,
                null,
                24L,
                new BigDecimal("38.20"),
                true,
                6L,
                createdAt,
                createdAt
        );

        List<PostListItemResponse> result = assembler.assembleList(
                List.of(new PostListKeyRow(
                        154L,
                        createdAt,
                        null
                )),
                List.of(row),
                7L,
                List.of(new PostReactionCountRow(
                        154L,
                        2L,
                        1L,
                        true
                )),
                List.of(unavailable, available),
                List.of(pending, image)
        );

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.contentPreview())
                    .isEqualTo("첫 줄 둘째 줄");
            assertThat(item.thumbnailUrl())
                    .isEqualTo("https://example.com/401");
            assertThat(item.likeCount()).isEqualTo(2L);
            assertThat(item.commentCount()).isEqualTo(1L);
            assertThat(item.likedByMe()).isTrue();
            assertThat(item.isMine()).isTrue();
            assertThat(item.isHot()).isTrue();
            assertThat(item.hotRank()).isEqualTo(6L);
            assertThat(item.apartment().name())
                    .isEqualTo("래미안 옥수 리버젠");
            assertThat(item.createdAt().getOffset())
                    .isEqualTo(ZoneOffset.ofHours(9));
        });
        verify(mediaAccessUrlProvider).issueAll(argThat(fileIds ->
                fileIds.size() == 1 && fileIds.contains(401L)));
    }

    @Test
    void assemblesMemberPostWithAvailableAttachmentAndPermissions() {
        PostAttachment attachment =
                PostAttachment.create(154L, 401L, 0);
        MediaFileSnapshot file = new MediaFileSnapshot(
                401L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "station-route.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                NOW.plusSeconds(600),
                null
        );
        when(mediaAccessUrlProvider.issue(401L))
                .thenReturn(new MediaAccessUrl(
                        "https://example.com/401",
                        NOW.plusSeconds(300)
                ));

        PostDetailResponse response = assembler.assembleDetail(
                memberRow(),
                7L,
                25L,
                new PostInteractionRow(
                        2L,
                        1L,
                        true,
                        new BigDecimal("17.70"),
                        false,
                        9L
                ),
                List.of(attachment),
                List.of(file)
        );

        assertThat(response.originalAvailable()).isTrue();
        assertThat(response.author().memberId()).isEqualTo(7L);
        assertThat(response.author().authorType()).isEqualTo("MEMBER");
        assertThat(response.isMine()).isTrue();
        assertThat(response.permissions().canEdit()).isTrue();
        assertThat(response.permissions().canDelete()).isTrue();
        assertThat(response.permissions().canLike()).isTrue();
        assertThat(response.permissions().canComment()).isTrue();
        assertThat(response.attachments()).singleElement()
                .satisfies(item -> {
                    assertThat(item.displayOrder()).isEqualTo(1);
                    assertThat(item.available()).isTrue();
                    assertThat(item.fileUrl())
                            .isEqualTo("https://example.com/401");
                    assertThat(item.expiresAt().getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                });
    }

    @Test
    void assemblesUpdatedPostWithoutDetailOnlyAttachmentFields() {
        PostAttachment attachment =
                PostAttachment.create(154L, 401L, 0);
        MediaFileSnapshot file = new MediaFileSnapshot(
                401L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "station-route.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                NOW.plusSeconds(600),
                null
        );
        when(mediaAccessUrlProvider.issue(401L))
                .thenReturn(new MediaAccessUrl(
                        "https://example.com/401",
                        NOW.plusSeconds(300)
                ));

        PostUpdateResponse response = assembler.assembleUpdatedPost(
                memberRow(),
                7L,
                12L,
                new PostInteractionRow(
                        2L,
                        1L,
                        false,
                        new BigDecimal("22.10"),
                        true,
                        1L
                ),
                List.of(attachment),
                List.of(file)
        );

        assertThat(response.author().memberId()).isEqualTo(7L);
        assertThat(response.apartment().apartmentId()).isEqualTo(15L);
        assertThat(response.viewCount()).isEqualTo(12L);
        assertThat(response.likeCount()).isEqualTo(2L);
        assertThat(response.commentCount()).isEqualTo(1L);
        assertThat(response.isMine()).isTrue();
        assertThat(response.isHot()).isTrue();
        assertThat(response.attachments()).singleElement()
                .satisfies(item -> {
                    assertThat(item.fileId()).isEqualTo(401L);
                    assertThat(item.fileUrl())
                            .isEqualTo("https://example.com/401");
                    assertThat(item.displayOrder()).isEqualTo(1);
                });
    }

    @Test
    void assemblesAutoReportWithSystemAuthorAndHotRank() {
        PostDetailResponse response = assembler.assembleDetail(
                autoReportRow(),
                7L,
                83L,
                new PostInteractionRow(
                        5L,
                        3L,
                        true,
                        new BigDecimal("125.60"),
                        true,
                        2L
                ),
                List.of(),
                List.of()
        );

        assertThat(response.author().memberId()).isNull();
        assertThat(response.author().nickname())
                .isEqualTo("싸방팔방 리포트");
        assertThat(response.author().selectedCharacterId())
                .isEqualTo("PALBANG");
        assertThat(response.author().authorType()).isEqualTo("SYSTEM");
        assertThat(response.report().reportAvailable()).isTrue();
        assertThat(response.isMine()).isFalse();
        assertThat(response.permissions().canEdit()).isFalse();
        assertThat(response.permissions().canDelete()).isFalse();
        assertThat(response.isHot()).isTrue();
        assertThat(response.hotRank()).isEqualTo(2L);
    }

    @Test
    void anonymizesWithdrawnAuthorAndBlocksUnavailableAttachmentUrl() {
        PostAttachment attachment =
                PostAttachment.create(154L, 401L, 0);
        MediaFileSnapshot deletedFile = new MediaFileSnapshot(
                401L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "old.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                null,
                NOW.minusSeconds(1)
        );

        PostDetailResponse response = assembler.assembleDetail(
                withdrawnAuthorRow(),
                8L,
                4L,
                new PostInteractionRow(
                        0L,
                        0L,
                        false,
                        null,
                        null,
                        null
                ),
                List.of(attachment),
                List.of(deletedFile)
        );

        assertThat(response.author().memberId()).isNull();
        assertThat(response.author().nickname()).isEqualTo("탈퇴한 회원");
        assertThat(response.author().profileImageUrl()).isNull();
        assertThat(response.attachments()).singleElement()
                .satisfies(item -> {
                    assertThat(item.available()).isFalse();
                    assertThat(item.fileUrl()).isNull();
                    assertThat(item.expiresAt()).isNull();
                });
        assertThat(response.isHot()).isFalse();
        assertThat(response.hotRank()).isNull();
        verifyNoInteractions(mediaAccessUrlProvider);
    }

    private PostDetailRow memberRow() {
        return row(
                false,
                7L,
                MemberStatus.ACTIVE,
                null,
                null,
                null
        );
    }

    private PostDetailRow autoReportRow() {
        return row(
                true,
                null,
                null,
                null,
                48L,
                ReportStatus.DONE
        );
    }

    private PostDetailRow withdrawnAuthorRow() {
        return row(
                false,
                7L,
                MemberStatus.WITHDRAWN,
                NOW.minusSeconds(3600),
                null,
                null
        );
    }

    private PostDetailRow row(
            boolean autoReport,
            Long authorId,
            MemberStatus authorStatus,
            Instant authorDeletedAt,
            Long reportId,
            ReportStatus reportStatus
    ) {
        Instant createdAt = Instant.parse("2026-07-29T07:30:00Z");
        return new PostDetailRow(
                154L,
                BoardType.INFORMATION,
                "제목",
                "본문",
                PostStatus.ACTIVE,
                autoReport,
                authorId,
                "집보는다람쥐",
                "https://example.com/profile.jpg",
                "JIPKONG",
                authorStatus,
                authorDeletedAt,
                15L,
                "래미안 옥수 리버젠",
                "서울특별시 성동구 매봉길 15",
                reportId,
                reportStatus,
                createdAt,
                createdAt
        );
    }
}
