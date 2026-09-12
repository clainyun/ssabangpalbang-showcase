package com.ssafy.ssabangpalbang.report.client;

import com.ssafy.ssabangpalbang.report.config.ReportRagIndexProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RequiredArgsConstructor
public class RestReportRagIndexClient implements ReportRagIndexClient {

    private final RestClient restClient;
    private final ReportRagIndexProperties properties;

    @Override
    public ReportRagIndexResponse index(Long reportId) {
        try {
            ReportRagIndexResponse response = restClient.post()
                    .uri(properties.indexPath(), reportId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ReportRagIndexResponse.class);

            if (response == null) {
                throw new ReportRagIndexException(
                        "RAG 색인 서비스가 빈 응답을 반환했습니다."
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new ReportRagIndexException(
                    "RAG 색인 서비스가 HTTP "
                            + exception.getStatusCode().value()
                            + "를 반환했습니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new ReportRagIndexException(
                    "RAG 색인 서비스에 연결하지 못했습니다.",
                    exception
            );
        }
    }
}
