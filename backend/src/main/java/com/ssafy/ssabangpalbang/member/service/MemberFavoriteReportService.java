package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.apartment.repository.ApartmentImageKeyRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentImageUrlResolver;
import com.ssafy.ssabangpalbang.apartment.support.ReportResultJsonParser;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteReportResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.repository.FavoriteReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberFavoriteReportService {

    private final MemberRepository memberRepository;
    private final ReportRepository reportRepository;
    private final ReportResultJsonParser reportResultJsonParser;
    private final ApartmentRepository apartmentRepository;
    private final ApartmentImageUrlResolver apartmentImageUrlResolver;

    @Transactional(readOnly = true)
    public PageResponse<MemberFavoriteReportResponse> getFavoriteReports(
            Long memberId,
            int page,
            int size
    ) {
        validateActiveMember(memberId);

        Page<FavoriteReportRow> rows = reportRepository
                .findFavoriteDoneReports(
                        memberId,
                        PageRequest.of(page, size)
                );

        // 리포트가 다루는 단지의 대표 이미지 URL을 배치 조회(N+1 방지).
        // 미매칭·미설정이면 맵에 없어 imageUrl이 null → 프론트가 로컬 일러스트로 폴백.
        List<Long> apartmentIds = rows.getContent().stream()
                .map(FavoriteReportRow::getApartmentId)
                .toList();
        Map<Long, String> imageUrls = new HashMap<>();
        if (!apartmentIds.isEmpty()) {
            for (ApartmentImageKeyRow keyRow : apartmentRepository
                    .findImageObjectKeysByApartmentIds(apartmentIds)) {
                String url = apartmentImageUrlResolver.resolve(keyRow.getObjectKey());
                if (url != null) {
                    imageUrls.put(keyRow.getApartmentId(), url);
                }
            }
        }

        return PageResponse.from(rows.map(row -> {
            ReportResultJsonParser.ParsedReport result =
                    reportResultJsonParser.parse(
                            row.getReportId(),
                            row.getResultJson()
                    );
            return MemberFavoriteReportResponse.from(
                    row,
                    result.title(),
                    result.summary(),
                    result.analysisTags(),
                    imageUrls.get(row.getApartmentId())
            );
        }));
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
