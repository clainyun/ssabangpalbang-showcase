package com.ssafy.ssabangpalbang.community.integration;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.repository.AutoReportPostRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoReportPostListenerTest {

    private static final long REPORT_ID = 48L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private AutoReportPostRepository autoReportPostRepository;

    @Test
    void 완료_리포트로_시스템_정보글을_작성한다() {
        AutoReportPostListener listener = listener();
        when(autoReportPostRepository.findCompletedReportSource(REPORT_ID))
                .thenReturn(Optional.of(source()));

        listener.createBeforeCommit(new ReportCompletedEvent(REPORT_ID, 7L));

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).flush();
        verify(autoReportPostRepository).insertIfAbsent(postCaptor.capture());
        Post post = postCaptor.getValue();
        assertThat(post.getBoardType()).isEqualTo(BoardType.INFORMATION);
        assertThat(post.isAutoReport()).isTrue();
        assertThat(post.getAuthorId()).isNull();
        assertThat(post.getReportId()).isEqualTo(REPORT_ID);
        assertThat(post.getApartmentId()).isEqualTo(15L);
        assertThat(post.getTitle()).isEqualTo("옥수 임장 리포트");
        assertThat(post.getContent()).isEqualTo("""
                아파트: 래미안 옥수 리버젠
                임장일: 2026-08-02
                요약: 교통 접근성이 좋고 보행 환경을 함께 확인했습니다.

                리포트 상세: /api/v1/reports/48""");
    }

    @Test
    void 원본을_구성할_수_없으면_예외를_전파해_완료_트랜잭션을_롤백한다() {
        AutoReportPostListener listener = listener();
        when(autoReportPostRepository.findCompletedReportSource(REPORT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.createBeforeCommit(
                new ReportCompletedEvent(REPORT_ID, 7L)
        )).isInstanceOf(IllegalStateException.class);

        verify(postRepository).flush();
    }

    private AutoReportPostListener listener() {
        return new AutoReportPostListener(
                postRepository,
                autoReportPostRepository
        );
    }

    private AutoReportPostRepository.CompletedReportSource source() {
        return new AutoReportPostRepository.CompletedReportSource(
                REPORT_ID,
                15L,
                "래미안 옥수 리버젠",
                Instant.parse("2026-08-02T03:30:00Z"),
                "옥수 임장 리포트",
                "교통 접근성이 좋고 보행 환경을 함께 확인했습니다."
        );
    }
}
