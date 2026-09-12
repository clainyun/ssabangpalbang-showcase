package com.ssafy.ssabangpalbang.demoseed;

import com.ssafy.ssabangpalbang.report.client.ReportRagIndexClient;
import com.ssafy.ssabangpalbang.report.client.ReportRagIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 적재한 리포트를 AI 서비스에 색인 요청한다.
 *
 * <p><b>왜 필요한가.</b> 리포트 RAG 색인은 {@code ReportCompletedEvent}(AFTER_COMMIT)에 붙어 있다
 * ({@code ReportRagIndexListener}). 시더는 DB에 리포트를 직접 넣으므로 그 이벤트가 발생하지 않고,
 * 그대로 두면 색인 0건이라 챗봇이 리포트를 근거로 쓰지 못한다.</p>
 *
 * <p><b>왜 이벤트를 발행하지 않는가.</b> 같은 이벤트에 알림·자동 게시글·<b>스터디 완료 처리</b>
 * 리스너가 셋 더 붙어 있다. 발행하면 스터디 상태가 덮이고 자동 게시글이 중복 생성되어 시연이 깨진다.
 * 그래서 색인 클라이언트만 직접 호출한다.</p>
 *
 * <p>운영 리스너는 색인 실패를 {@code log.warn}으로만 남기고 넘어간다. 시더는 그러면 안 된다 —
 * 색인이 안 된 걸 모르고 시연에 들어가면 챗봇이 "근거 없음"으로 답한다. 그래서 결과를 집계해
 * 실패가 있으면 예외로 알린다.</p>
 */
@Service
@Profile("demoseed")
public class DemoSeedReportIndexer {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedReportIndexer.class);

    private final ReportRagIndexClient client;

    public DemoSeedReportIndexer(ReportRagIndexClient client) {
        this.client = client;
    }

    public void indexAll(List<Long> reportIds) {
        if (reportIds.isEmpty()) {
            log.info("[demoseed] 색인할 리포트가 없습니다.");
            return;
        }

        int indexed = 0;
        int chunks = 0;
        List<String> failures = new java.util.ArrayList<>();

        for (Long reportId : reportIds) {
            try {
                ReportRagIndexResponse response = client.index(reportId);
                if (response.indexed()) {
                    indexed++;
                    chunks += response.chunkCount();
                    log.info(
                            "[demoseed] 리포트 색인 완료: reportId={} chunkCount={} deletedCount={}",
                            reportId,
                            response.chunkCount(),
                            response.deletedCount()
                    );
                } else {
                    failures.add("reportId=" + reportId + " skipReason=" + response.skipReason());
                }
            } catch (RuntimeException exception) {
                failures.add("reportId=" + reportId + " error=" + exception.getMessage());
            }
        }

        log.info(
                "[demoseed] RAG 색인 결과: 성공 {}/{}건, 청크 {}개",
                indexed,
                reportIds.size(),
                chunks
        );

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "리포트 RAG 색인이 일부 실패했습니다. AI 서비스가 떠 있는지, "
                            + "ssabangpalbang.report.rag-index.base-url 이 맞는지 확인하십시오.\n"
                            + String.join("\n", failures)
            );
        }
    }
}
