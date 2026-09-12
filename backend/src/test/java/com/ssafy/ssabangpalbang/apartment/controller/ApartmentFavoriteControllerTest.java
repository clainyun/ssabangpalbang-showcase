package com.ssafy.ssabangpalbang.apartment.controller;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResult;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentResponseCode;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentService;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApartmentFavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private StudyService studyService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(7L),
                        null
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putReturnsFavoriteSuccess() throws Exception {
        stubAdd(
                ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS,
                3
        );

        mockMvc.perform(put("/api/v1/apartments/15/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_FAVORITE_SUCCESS"))
                .andExpect(jsonPath("$.data.favoritedByMe").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(3));
    }

    @Test
    void duplicatePutReturnsAlreadyExists() throws Exception {
        stubAdd(
                ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS,
                3
        );

        mockMvc.perform(put("/api/v1/apartments/15/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_FAVORITE_ALREADY_EXISTS"));
    }

    @Test
    void deleteReturnsUnfavoriteSuccess() throws Exception {
        stubRemove(
                ApartmentResponseCode.APARTMENT_UNFAVORITE_SUCCESS,
                2
        );

        mockMvc.perform(delete("/api/v1/apartments/15/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_UNFAVORITE_SUCCESS"))
                .andExpect(jsonPath("$.data.favoritedByMe").value(false));
    }

    @Test
    void duplicateDeleteReturnsFavoriteNotFoundAsSuccess() throws Exception {
        stubRemove(
                ApartmentResponseCode.APARTMENT_FAVORITE_NOT_FOUND,
                2
        );

        mockMvc.perform(delete("/api/v1/apartments/15/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_FAVORITE_NOT_FOUND"));
    }

    @Test
    void invalidApartmentIdReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/apartments/0/favorite"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_ID_INVALID"));
    }

    @Test
    void missingApartmentReturnsNotFound() throws Exception {
        when(apartmentService.addFavorite(7L, 999L))
                .thenThrow(new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND
                ));

        mockMvc.perform(put("/api/v1/apartments/999/favorite"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("APARTMENT_NOT_FOUND"));
    }

    @Test
    void responseExcludesInternalFields() throws Exception {
        stubAdd(
                ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS,
                1
        );

        mockMvc.perform(put("/api/v1/apartments/15/favorite"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.memberId").doesNotExist());
    }

    @Test
    void responseUsesCanonicalFavoriteFieldName() throws Exception {
        stubAdd(
                ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS,
                1
        );

        mockMvc.perform(put("/api/v1/apartments/15/favorite"))
                .andExpect(jsonPath("$.data.favoritedByMe").exists())
                .andExpect(jsonPath("$.data.favorited").doesNotExist());
    }

    private void stubAdd(
            ApartmentResponseCode responseCode,
            int favoriteCount
    ) {
        when(apartmentService.addFavorite(7L, 15L))
                .thenReturn(result(responseCode, true, favoriteCount));
    }

    private void stubRemove(
            ApartmentResponseCode responseCode,
            int favoriteCount
    ) {
        when(apartmentService.removeFavorite(7L, 15L))
                .thenReturn(result(responseCode, false, favoriteCount));
    }

    private ApartmentFavoriteResult result(
            ApartmentResponseCode responseCode,
            boolean favoritedByMe,
            int favoriteCount
    ) {
        return new ApartmentFavoriteResult(
                responseCode,
                new ApartmentFavoriteResponse(
                        15L,
                        favoritedByMe,
                        favoriteCount
                )
        );
    }
}
