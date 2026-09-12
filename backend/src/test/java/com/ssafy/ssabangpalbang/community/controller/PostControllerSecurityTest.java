package com.ssafy.ssabangpalbang.community.controller;

import com.ssafy.ssabangpalbang.auth.config.AuthSecurityConfiguration;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.community.service.PostCommandService;
import com.ssafy.ssabangpalbang.community.service.PostQueryService;
import com.ssafy.ssabangpalbang.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({
        AuthSecurityConfiguration.class,
        GlobalExceptionHandler.class
})
class PostControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCommandService postCommandService;

    @MockitoBean
    private PostQueryService postQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void rejectsPostCreationWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "FREE",
                                  "title": "제목",
                                  "content": "본문"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsPostDetailWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/posts/154"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postQueryService, jwtTokenProvider);
    }

    @Test
    void rejectsPostListWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postQueryService, jwtTokenProvider);
    }

    @Test
    void rejectsPostUpdateWithoutAccessToken() throws Exception {
        mockMvc.perform(patch("/api/v1/posts/154")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 제목\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsPostDeletionWithoutAccessToken() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/154"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsPostLikeWithoutAccessToken() throws Exception {
        mockMvc.perform(put("/api/v1/posts/154/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postCommandService, jwtTokenProvider);
    }

    @Test
    void rejectsPostUnlikeWithoutAccessToken() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/154/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_ACCESS_TOKEN_INVALID"));

        verifyNoInteractions(postCommandService, jwtTokenProvider);
    }
}
