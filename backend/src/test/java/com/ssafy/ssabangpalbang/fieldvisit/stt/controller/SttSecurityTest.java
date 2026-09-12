package com.ssafy.ssabangpalbang.fieldvisit.stt.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttCreateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.response.SttStatusResponse;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SttController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class SttSecurityTest {

    private static final String URI =
            "/api/v1/studies/7/field-visit/stt";
    private static final String STATUS_URI =
            "/api/v1/studies/7/field-visit/stt/stt-auth";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SttService sttService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post(URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(sttService);
    }

    @Test
    void 유효한_Access_Token으로_STT_요청을_접수한다() throws Exception {
        when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(7L);
        when(sttService.createStt(eq(7L), any(SttCreateRequest.class)))
                .thenReturn(new SttService.CreateResult(
                        HttpStatus.ACCEPTED,
                        SttResponseCode.FIELD_STT_ACCEPTED,
                        new SttCreateResponse(
                                "stt-auth",
                                7L,
                                100L,
                                90L,
                                501L,
                                SttStatus.PENDING,
                                null,
                                OffsetDateTime.now(ZoneOffset.ofHours(9))
                        )
                ));

        mockMvc.perform(post(URI)
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("FIELD_STT_ACCEPTED"));
    }

    @Test
    void 상태_조회에_Access_Token이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get(STATUS_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(sttService);
    }

    @Test
    void 유효한_Access_Token으로_STT_상태를_조회한다() throws Exception {
        OffsetDateTime requestedAt =
                OffsetDateTime.now(ZoneOffset.ofHours(9));
        when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(7L);
        when(sttService.getSttStatus(7L, "stt-auth"))
                .thenReturn(new SttStatusResponse(
                        "stt-auth",
                        7L,
                        501L,
                        SttStatus.PROCESSING,
                        null,
                        null,
                        null,
                        false,
                        requestedAt,
                        null
                ));

        mockMvc.perform(get(STATUS_URI)
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("FIELD_STT_STATUS_SUCCESS"));
    }

    private String validRequest() {
        return """
                {
                  "audioFileId": 90,
                  "checklistItemId": 501,
                  "clientRequestId": "81197c8f-780b-40c2-abf6-b83473de9c82"
                }
                """;
    }
}
