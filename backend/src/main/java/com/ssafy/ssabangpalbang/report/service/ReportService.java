package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.dto.response.MemberReportResponse;
import com.ssafy.ssabangpalbang.report.repository.MemberReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<MemberReportResponse> getMyReports(
            Long memberId,
            int page,
            int size
    ) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        Page<MemberReportRow> rows = reportRepository
                .findAccessibleDoneReports(
                        memberId,
                        PageRequest.of(page, size)
                );

        return PageResponse.from(rows.map(row -> {
            ParsedReportResult result = parseResult(
                    row.getReportId(),
                    row.getResultJson()
            );
            return MemberReportResponse.from(
                    row,
                    result.title(),
                    result.summary(),
                    result.analysisTags()
            );
        }));
    }

    private ParsedReportResult parseResult(
            Long reportId,
            String resultJson
    ) {
        if (resultJson == null || resultJson.isBlank()) {
            return ParsedReportResult.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return new ParsedReportResult(
                    textOrNull(root.get("title")),
                    textOrNull(root.get("summary")),
                    stringList(root.get("analysisTags"))
            );
        } catch (JsonProcessingException exception) {
            log.warn(
                    "리포트 목록용 결과 JSON을 읽지 못했습니다. reportId={}",
                    reportId
            );
            return ParsedReportResult.empty();
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.textValue();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.textValue());
            }
        });
        return List.copyOf(values);
    }

    private record ParsedReportResult(
            String title,
            String summary,
            List<String> analysisTags
    ) {
        private static ParsedReportResult empty() {
            return new ParsedReportResult(null, null, List.of());
        }
    }
}
