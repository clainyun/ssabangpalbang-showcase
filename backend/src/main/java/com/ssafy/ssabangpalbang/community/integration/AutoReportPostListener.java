package com.ssafy.ssabangpalbang.community.integration;

import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.repository.AutoReportPostRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.report.integration.ReportCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists the system-owned information post in the report completion
 * transaction.  BEFORE_COMMIT is intentional: a post composition or insert
 * failure must roll back the corresponding report completion.
 */
@Component
@RequiredArgsConstructor
public class AutoReportPostListener {

    private final PostRepository postRepository;
    private final AutoReportPostRepository autoReportPostRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createBeforeCommit(ReportCompletedEvent event) {
        // Report.complete() changes a managed JPA entity. Flush it before the
        // JDBC query so the source query observes DONE and result_json.
        postRepository.flush();

        AutoReportPostRepository.CompletedReportSource source =
                autoReportPostRepository.findCompletedReportSource(
                                event.reportId()
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "완료된 리포트의 자동 게시글 원본을 찾을 수 없습니다."
                        ));

        Post post = Post.createAutomaticReportPost(
                source.reportId(),
                source.apartmentId(),
                source.apartmentName(),
                source.fieldSessionStartedAt(),
                source.title(),
                source.summary()
        );
        autoReportPostRepository.insertIfAbsent(post);
    }
}
