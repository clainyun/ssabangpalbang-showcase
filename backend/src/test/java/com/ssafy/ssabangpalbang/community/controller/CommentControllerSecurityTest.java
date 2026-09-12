package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class CommentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentCommandService commentCommandService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void rejectsCommentDeletionWithoutAccessToken()
            throws Exception {
        mockMvc.perform(delete("/api/v1/comments/36"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(commentCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsCommentUpdateWithoutAccessToken()
            throws Exception {
        mockMvc.perform(patch("/api/v1/comments/36")
                        .contentType("application/json")
                        .content("""
                                {"content":"수정할 댓글"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(commentCommandService, jwtTokenProvider);
    }
}
