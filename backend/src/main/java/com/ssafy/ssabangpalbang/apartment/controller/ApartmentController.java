package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentTransactionSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentBoundsCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentReportSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDistrictSummaryResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResult;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentResponseCode;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentTransactionResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentBoundsResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentReportItem;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.study.dto.request.ApartmentStudySort;
import com.ssafy.ssabangpalbang.study.dto.response.ApartmentStudyResponse;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/apartments")
@RequiredArgsConstructor
@Validated
@Tag(name = "아파트", description = "아파트 조회와 실거래 목록")
public class ApartmentController {

    private final ApartmentService apartmentService;
    private final StudyService studyService;

    @GetMapping("/bounds")
    @Operation(
            summary = "지도 가시 영역 아파트 조회",
            description = "지도 경계 안의 아파트와 최신 거래, 모집 스터디 수, 완료 리포트 수, 내 찜 여부를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지도 영역 아파트 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "좌표 누락·범위·순서·영역 크기가 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음")
    })
    public ApiResponse<ApartmentBoundsResponse> getBounds(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "남서쪽 위도", example = "37.4600")
            @RequestParam(required = false) Double southWestLat,
            @Parameter(description = "남서쪽 경도", example = "127.0000")
            @RequestParam(required = false) Double southWestLng,
            @Parameter(description = "북동쪽 위도", example = "37.5400")
            @RequestParam(required = false) Double northEastLat,
            @Parameter(description = "북동쪽 경도", example = "127.1300")
            @RequestParam(required = false) Double northEastLng
    ) {
        ApartmentBoundsCondition condition = ApartmentBoundsCondition.of(
                southWestLat, southWestLng, northEastLat, northEastLng
        );
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_BOUNDS_SUCCESS,
                apartmentService.getBounds(authenticatedMember.memberId(), condition)
        );
    }

    @GetMapping("/districts/summary")
    @Operation(
            summary = "자치구별 아파트 집계 조회",
            description = "서울 25개 자치구별 아파트 단지 수와 중심 좌표를 조회합니다. 지도 축소 상태의 구 단위 표시에 사용합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "자치구별 아파트 집계 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "서버 내부 오류")
    })
    public ApiResponse<ApartmentDistrictSummaryResponse> getDistrictSummary(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_DISTRICT_SUMMARY_SUCCESS,
                apartmentService.getDistrictSummary(authenticatedMember.memberId())
        );
    }

    @GetMapping
    @Operation(
            summary = "아파트 키워드·현재 위치 주변 검색",
            description = "키워드·지역 또는 현재 위치 반경으로 아파트를 검색합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "아파트 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색 조건이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 지역 정보를 찾을 수 없음")
    })
    public ApiResponse<ApartmentSearchResponse> search(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "아파트명·동명·주소 검색어", example = "래미안")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "서울 자치구 코드", example = "11680")
            @RequestParam(required = false) String districtCode,
            @Parameter(description = "법정동 코드", example = "1168010100")
            @RequestParam(required = false) String dongCode,
            @Parameter(description = "현재 위도", example = "37.4979")
            @RequestParam(required = false) Double latitude,
            @Parameter(description = "현재 경도", example = "127.0276")
            @RequestParam(required = false) Double longitude,
            @Parameter(description = "검색 반경(m)", example = "3000")
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        ApartmentSearchCondition condition = ApartmentSearchCondition.of(
                keyword, districtCode, dongCode, latitude, longitude,
                radiusMeters, page, size
        );
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_LIST_SUCCESS,
                apartmentService.search(authenticatedMember.memberId(), condition)
        );
    }

    @GetMapping("/{apartmentId}")
    @Operation(
            summary = "아파트 상세 조회",
            description = "단지 기본 정보와 대표 이미지 URL, 최근 실거래, 모집 스터디 수, 완료 리포트 수, 내 찜 여부를 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "아파트 상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "아파트 ID 형식이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ApiResponse<ApartmentDetailResponse> getDetail(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "아파트 ID", example = "1")
            @PathVariable Long apartmentId
    ) {
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_DETAIL_SUCCESS,
                apartmentService.getDetail(
                        authenticatedMember.memberId(),
                        apartmentId
                )
        );
    }

    @GetMapping("/{apartmentId}/transactions")
    @Operation(
            summary = "아파트 실거래 목록 조회",
            description = "취소되지 않은 정상 거래만 최신순으로 반환합니다. 가격은 만 원 단위입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "실거래 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "아파트 ID·전용면적·연도·정렬 기준 또는 페이지 값이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트를 찾을 수 없음")
    })
    public ApiResponse<PageResponse<ApartmentTransactionResponse>> getTransactions(
            @PathVariable Long apartmentId,
            @Parameter(description = "전용면적(㎡). ±0.05 범위로 조회한다.", example = "84.8")
            @RequestParam(required = false) Double exclusiveArea,
            @Parameter(description = "계약 연도. 미래 연도는 조회할 수 없다.", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "정렬 기준.", example = "DEAL_DATE_DESC")
            @RequestParam(defaultValue = "DEAL_DATE_DESC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (apartmentId <= 0) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_ID_INVALID,
                    Map.of("field", "apartmentId", "reason", "아파트 ID는 1 이상이어야 합니다.")
            );
        }
        if (page < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "page", "reason", "페이지 번호는 0 이상이어야 합니다.")
            );
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "size", "reason", "페이지 크기는 1 이상 100 이하이어야 합니다.")
            );
        }
        ApartmentTransactionSearchCondition condition =
                ApartmentTransactionSearchCondition.of(exclusiveArea, year, sort, LocalDate.now());
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_TRANSACTION_LIST_SUCCESS,
                apartmentService.getTransactions(apartmentId, condition, page, size)
        );
    }

    @GetMapping("/{apartmentId}/reports")
    @Operation(
            summary = "아파트별 완료 리포트 목록 조회",
            description = "공개된 완료 AI 임장 리포트를 최신 완료순으로 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료 리포트 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "아파트 ID 또는 페이지 값이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ApiResponse<PageResponse<ApartmentReportItem>> getReports(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "아파트 ID", example = "1")
            @PathVariable Long apartmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_REPORT_LIST_SUCCESS,
                apartmentService.getReports(
                        authenticatedMember.memberId(),
                        ApartmentReportSearchCondition.of(
                                apartmentId,
                                page,
                                size
                        )
                )
        );
    }

    @GetMapping("/{apartmentId}/studies")
    @Operation(
            summary = "아파트별 모집 스터디 목록 조회",
            description = "모집 중이고 정원이 남은 스터디만 반환합니다. 시작 시각이 지난 일정의 스터디는 제외합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "아파트 모집 스터디 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "아파트 ID·정렬 기준 또는 페이지 값이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token이 없거나 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "아파트 또는 회원 정보를 찾을 수 없음")
    })
    public ApiResponse<PageResponse<ApartmentStudyResponse>> getRecruitingStudies(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long apartmentId,
            @Parameter(description = "정렬 기준.", example = "SCHEDULE_ASC")
            @RequestParam(defaultValue = "SCHEDULE_ASC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (apartmentId <= 0) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_ID_INVALID,
                    Map.of("field", "apartmentId", "reason", "아파트 ID는 1 이상의 숫자여야 합니다.")
            );
        }
        if (page < 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "page", "reason", "페이지 번호는 0 이상이어야 합니다.")
            );
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("field", "size", "reason", "페이지 크기는 1 이상 100 이하이어야 합니다.")
            );
        }
        return ApiResponse.success(
                ApartmentResponseCode.APARTMENT_STUDY_LIST_SUCCESS,
                studyService.getRecruitingStudies(
                        authenticatedMember.memberId(),
                        apartmentId,
                        ApartmentStudySort.from(sort),
                        page,
                        size
                )
        );
    }

    @PutMapping("/{apartmentId}/favorite")
    @Operation(
            summary = "아파트 찜 등록",
            description = "로그인 회원의 아파트 찜을 멱등하게 등록합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "아파트 찜 성공(이미 찜한 상태여도 200)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "아파트 ID가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "아파트 또는 회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<ApartmentFavoriteResponse> addFavorite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "아파트 ID", example = "12")
            @PathVariable Long apartmentId
    ) {
        validateApartmentId(apartmentId);
        ApartmentFavoriteResult result = apartmentService.addFavorite(
                authenticatedMember.memberId(),
                apartmentId
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    @DeleteMapping("/{apartmentId}/favorite")
    @Operation(
            summary = "아파트 찜 해제",
            description = "로그인 회원의 아파트 찜을 멱등하게 해제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "아파트 찜 해제 성공(찜하지 않은 상태여도 200)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "아파트 ID가 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "아파트 또는 회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<ApartmentFavoriteResponse> removeFavorite(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Parameter(description = "아파트 ID", example = "12")
            @PathVariable Long apartmentId
    ) {
        validateApartmentId(apartmentId);
        ApartmentFavoriteResult result = apartmentService.removeFavorite(
                authenticatedMember.memberId(),
                apartmentId
        );
        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    private void validateApartmentId(Long apartmentId) {
        if (apartmentId == null || apartmentId <= 0) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_ID_INVALID,
                    Map.of(
                            "field", "apartmentId",
                            "reason", "아파트 ID는 1 이상의 숫자여야 합니다."
                    )
            );
        }
    }
}
