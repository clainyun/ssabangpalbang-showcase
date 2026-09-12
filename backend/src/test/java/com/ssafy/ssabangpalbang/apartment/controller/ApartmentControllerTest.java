package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailLatestTransaction;
import java.math.BigDecimal;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentTransactionSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentTransactionResponse;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.study.dto.response.ApartmentStudyResponse;
import com.ssafy.ssabangpalbang.study.service.StudyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private StudyService studyService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedMember(7L), null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsApartmentDetail() throws Exception {
        when(apartmentService.getDetail(7L, 1L))
                .thenReturn(new ApartmentDetailResponse(
                        1L,
                        "래미안강남포레스트",
                        "서울특별시 강남구 개포로 310",
                        "11680",
                        "강남구",
                        "개포동",
                        37.4812,
                        127.0654,
                        2296,
                        "2020-09",
                        "https://cdn.example.com/apartment-images/v1/A13245.webp",
                        3100,
                        new BigDecimal("1.35"),
                        new ApartmentDetailLatestTransaction(
                                381L, 245000L, "TEN_THOUSAND_KRW",
                                new BigDecimal("84.80"),
                                LocalDate.of(2026, 7, 10), 15),
                        true, 2, 3, false
                ));

        mockMvc.perform(get("/api/v1/apartments/{apartmentId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.apartmentId").value(1L))
                .andExpect(jsonPath("$.data.imageUrl")
                        .value("https://cdn.example.com/apartment-images/v1/A13245.webp"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.complexCode").doesNotExist())
                .andExpect(jsonPath("$.data.latestTransaction.priceUnit")
                        .value("TEN_THOUSAND_KRW"))
                .andExpect(jsonPath("$.data.recruitingStudyCount").value(2))
                .andExpect(jsonPath("$.data.completedReportCount").value(3))
                .andExpect(jsonPath("$.data.favoritedByMe").value(false))
                .andExpect(jsonPath("$.data.legalDongCode").doesNotExist())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_DETAIL_SUCCESS"));
    }

    @Test
    void returnsNotFoundWhenApartmentDoesNotExist() throws Exception {
        when(apartmentService.getDetail(7L, 999L))
                .thenThrow(new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND
                ));

        mockMvc.perform(get("/api/v1/apartments/{apartmentId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_NOT_FOUND"));
    }

    @Test
    void returnsTransactionListWithExpectedStructureAndWithoutExcludedFields() throws Exception {
        PageResponse<ApartmentTransactionResponse> page = new PageResponse<>(
                List.of(new ApartmentTransactionResponse(
                        381L, LocalDate.of(2026, 7, 10), 245000L,
                        "TEN_THOUSAND_KRW", 84.80, 15
                )),
                12, 0, 20, 1
        );
        when(apartmentService.getTransactions(
                eq(1L), any(ApartmentTransactionSearchCondition.class), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/apartments/{apartmentId}/transactions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APARTMENT_TRANSACTION_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(12))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].buildYear").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].legalDongName").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].lotNumber").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].isCanceled").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].dedupKey").doesNotExist())
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").doesNotExist())
                .andExpect(jsonPath("$.data.dataUpdatedAt").doesNotExist());
    }

    @Test
    void rejectsInvalidApartmentId() throws Exception {
        mockMvc.perform(get("/api/v1/apartments/0/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_ID_INVALID"));
    }

    @Test
    void rejectsInvalidPageAndSize() throws Exception {
        assertCommonInvalidRequest("page", "-1");
        assertCommonInvalidRequest("size", "0");
        assertCommonInvalidRequest("size", "101");
    }

    @Test
    void rejectsUnknownSortWithAllowedValues() throws Exception {
        mockMvc.perform(get("/api/v1/apartments/1/transactions")
                        .param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_TRANSACTION_SORT_INVALID"))
                .andExpect(jsonPath("$.data.allowedValues.length()").value(6));
    }

    @Test
    void returnsTransactionNotFoundWithApartmentIdData() throws Exception {
        when(apartmentService.getTransactions(
                eq(25L), any(ApartmentTransactionSearchCondition.class), eq(0), eq(20)))
                .thenThrow(new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND,
                        Map.of("apartmentId", 25L)
                ));

        mockMvc.perform(get("/api/v1/apartments/25/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APARTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data.apartmentId").value(25L));
    }

    @Test
    void rejectsNonNumericApartmentId() throws Exception {
        mockMvc.perform(get("/api/v1/apartments/abc/transactions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsRecruitingStudyListWithExpectedContract() throws Exception {
        ApartmentStudyResponse item = new ApartmentStudyResponse(
                10L, "RECRUITING", "주말 임장", "소개", "목표", "RESIDENCE",
                4, 6, 2,
                new ApartmentStudyResponse.ScheduleSummary(
                        7L,
                        OffsetDateTime.of(2026, 7, 31, 15, 0, 0, 0, ZoneOffset.ofHours(9)),
                        null,
                        "3번 출구",
                        3),
                new ApartmentStudyResponse.LeaderSummary(
                        20L, "리더", "PALBANG", "THIRTIES"),
                "NONE", true, false, false
        );
        when(studyService.getRecruitingStudies(eq(7L), eq(15L), any(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(item), 1, 0, 20, 1));

        mockMvc.perform(get("/api/v1/apartments/15/studies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("APARTMENT_STUDY_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].schedule.dDay").value(3))
                .andExpect(jsonPath("$.data.content[0].leader.nickname").value("리더"))
                .andExpect(jsonPath("$.data.content[0].remainingCapacity").value(2))
                .andExpect(jsonPath("$.data.content[0].canApply").value(true))
                .andExpect(jsonPath("$.data.content[0].memberCount").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].nextScheduleAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].leader.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").doesNotExist());
    }

    @Test
    void rejectsInvalidRecruitingStudyParameters() throws Exception {
        mockMvc.perform(get("/api/v1/apartments/0/studies"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_ID_INVALID"))
                .andExpect(jsonPath("$.data.field").value("apartmentId"));
        mockMvc.perform(get("/api/v1/apartments/1/studies").param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APARTMENT_STUDY_SORT_INVALID"))
                .andExpect(jsonPath("$.data.allowedValues.length()").value(3));
        assertRecruitingStudyPageInvalid("page", "-1");
        assertRecruitingStudyPageInvalid("size", "0");
        assertRecruitingStudyPageInvalid("size", "101");
    }

    @Test
    void returnsRecruitingStudyApartmentNotFoundData() throws Exception {
        when(studyService.getRecruitingStudies(eq(7L), eq(25L), any(), eq(0), eq(20)))
                .thenThrow(new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND, Map.of("apartmentId", 25L)));

        mockMvc.perform(get("/api/v1/apartments/25/studies"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APARTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data.apartmentId").value(25L));
    }

    private void assertCommonInvalidRequest(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/apartments/1/transactions")
                        .param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value(parameter));
    }

    private void assertRecruitingStudyPageInvalid(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/v1/apartments/1/studies").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value(parameter));
    }
}
