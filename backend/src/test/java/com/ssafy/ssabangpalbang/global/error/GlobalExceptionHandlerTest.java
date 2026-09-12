package com.ssafy.ssabangpalbang.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(GlobalExceptionHandlerTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 성공_응답은_5개_필드를_선언_순서대로_반환한다() throws Exception {
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEST_SUCCESS"))
                .andExpect(jsonPath("$.message").value("테스트 요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.message").value("ok"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(result -> {
                    String responseBody = result.getResponse().getContentAsString();

                    assertThat(responseBody.indexOf("\"success\""))
                            .isLessThan(responseBody.indexOf("\"code\""))
                            .isLessThan(responseBody.indexOf("\"message\""))
                            .isLessThan(responseBody.indexOf("\"data\""))
                            .isLessThan(responseBody.indexOf("\"timestamp\""));
                });
    }

    @Test
    void 데이터_없는_성공_응답도_data_null을_포함한다() throws Exception {
        mockMvc.perform(post("/test/success-without-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 비즈니스_예외를_평면_오류_형식으로_반환한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("테스트 리소스를 찾을 수 없습니다."));
    }

    @Test
    void 비즈니스_예외의_컨텍스트를_data에_그대로_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-with-data"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.fileId").value(401));
    }

    @Test
    void 컨텍스트_없는_비즈니스_예외는_data_null을_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-without-data"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 입력값_검증_오류에_단일_필드_정보를_포함한다() throws Exception {
        mockMvc.perform(
                        post("/test/validation")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field")
                        .value("name"))
                .andExpect(jsonPath("$.data.reason")
                        .value("이름은 필수입니다."));
    }

    @Test
    void 다중_검증_오류는_필드명_오름차순_첫번째를_반환한다() throws Exception {
        mockMvc.perform(
                        post("/test/validation/multiple")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "",
                                          "content": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.field").value("content"))
                .andExpect(jsonPath("$.data.reason").value("본문은 필수입니다."));
    }

    @Test
    void 읽을_수_없는_JSON을_공통_오류_형식으로_반환한다() throws Exception {
        mockMvc.perform(
                        post("/test/body")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "name":
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 지원하지_않는_HTTP_메서드를_405로_반환한다() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_METHOD_NOT_ALLOWED"));
    }

    @Test
    void timestamp는_서울_오프셋을_포함한_ISO_8601_문자열이다() throws Exception {
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?\\+09:00$"
                )));
    }

    @Test
    void queryParameterValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/test/query-validation")
                                .queryParam("page", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("page"))
                .andExpect(jsonPath("$.data.reason")
                        .value("페이지는 0 이상이어야 합니다."));
    }

    @Test
    void pathParameterTypeMismatchReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/path-validation/invalid-member-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("memberId"))
                .andExpect(jsonPath("$.data.reason")
                        .value("요청 파라미터 값이 올바르지 않습니다."));
    }

    @Test
    void missingRequiredQueryParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/query-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data.field").value("page"))
                .andExpect(jsonPath("$.data.reason")
                        .value("필수 요청 파라미터입니다."));
    }
}
