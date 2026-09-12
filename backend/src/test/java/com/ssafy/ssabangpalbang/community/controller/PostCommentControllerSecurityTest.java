package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.community.service.CommentCommandService;
import com.ssafy.ssabangpalbang.community.service.CommentQueryService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostCommentController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class PostCommentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentCommandService commentCommandService;

    @MockitoBean
    private CommentQueryService commentQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void rejectsCommentCreationWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/posts/154/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(commentCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsCommentListWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/posts/154/comments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(
                commentCommandService,
                commentQueryService,
                jwtTokenProvider
        );
    }
}
