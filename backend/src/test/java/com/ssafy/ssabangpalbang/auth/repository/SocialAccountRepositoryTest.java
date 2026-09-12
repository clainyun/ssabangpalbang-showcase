package com.ssafy.ssabangpalbang.auth.repository;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:social-account-repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
@Sql(statements = {
        "DROP TABLE IF EXISTS social_account",
        "DROP TABLE IF EXISTS \"member\"",
        """
        CREATE TABLE "member" (
            id BIGINT PRIMARY KEY,
            email VARCHAR(255) NOT NULL,
            nickname VARCHAR(50) NOT NULL,
            profile_image_url VARCHAR(500),
            selected_character_id VARCHAR(20) NOT NULL,
            status VARCHAR(20) NOT NULL
        );
        """,
        """
        CREATE TABLE social_account (
            id BIGINT PRIMARY KEY,
            member_id BIGINT NOT NULL,
            provider VARCHAR(20) NOT NULL,
            social_user_id VARCHAR(255) NOT NULL,
            email VARCHAR(255),
            created_at TIMESTAMP WITH TIME ZONE NOT NULL
        );
        """
})
@Sql(
        statements = {
                "DROP TABLE IF EXISTS social_account",
                "DROP TABLE IF EXISTS \"member\""
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class SocialAccountRepositoryTest {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 제공자와_소셜_사용자_ID로_회원_스냅샷을_조회한다() {
        jdbcTemplate.update(
                """
                INSERT INTO "member" (
                    id,
                    email,
                    nickname,
                    profile_image_url,
                    selected_character_id,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                1L,
                "social@example.com",
                "소셜회원",
                null,
                "PALBANG",
                "ACTIVE"
        );
        jdbcTemplate.update(
                """
                INSERT INTO social_account (
                    id,
                    member_id,
                    provider,
                    social_user_id,
                    email,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                10L,
                1L,
                "KAKAO",
                "kakao-user-id",
                "social@example.com"
        );

        Optional<SocialMemberSnapshot> result =
                socialAccountRepository.findMemberSnapshot(
                        SocialProvider.KAKAO,
                        "kakao-user-id"
                );

        assertThat(result).contains(new SocialMemberSnapshot(
                1L,
                "social@example.com",
                "소셜회원",
                null,
                "PALBANG",
                MemberStatus.ACTIVE
        ));
        assertThat(socialAccountRepository.findMemberSnapshot(
                SocialProvider.NAVER,
                "kakao-user-id"
        )).isEmpty();
        assertThat(socialAccountRepository.existsByProviderAndSocialUserId(
                SocialProvider.KAKAO,
                "kakao-user-id"
        )).isTrue();
        assertThat(socialAccountRepository.existsByProviderAndSocialUserId(
                SocialProvider.NAVER,
                "kakao-user-id"
        )).isFalse();
    }
}
