package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class MemberRepositoryLocalIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void PostgreSQL에_회원을_저장하고_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        String email = "member-" + suffix + "@example.com";
        String nickname = "회원" + suffix;

        Member savedMember = memberRepository.saveAndFlush(
                new Member(email, "encoded-password", nickname)
        );

        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getCreatedAt()).isNotNull();
        assertThat(savedMember.getUpdatedAt()).isNotNull();
        assertThat(memberRepository.findByEmail(email))
                .contains(savedMember);
        assertThat(memberRepository.existsByEmail(email)).isTrue();
        assertThat(memberRepository.existsByNickname(nickname)).isTrue();
    }

    @Test
    void 회원_선호정보_존재_여부로_온보딩_완료를_판단한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member savedMember = memberRepository.saveAndFlush(
                new Member(
                        "preference-" + suffix + "@example.com",
                        "encoded-password",
                        "선호" + suffix
                )
        );

        assertThat(memberRepository.existsPreferenceByMemberId(
                savedMember.getId()
        )).isFalse();

        jdbcTemplate.update(
                "INSERT INTO member_preference (member_id) VALUES (?)",
                savedMember.getId()
        );

        assertThat(memberRepository.existsPreferenceByMemberId(
                savedMember.getId()
        )).isTrue();
    }

    @Test
    void 팔로우_관계를_방향에_맞게_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member follower = memberRepository.saveAndFlush(
                new Member(
                        "follower-" + suffix + "@example.com",
                        "encoded-password",
                        "팔로워" + suffix
                )
        );
        Member following = memberRepository.saveAndFlush(
                new Member(
                        "following-" + suffix + "@example.com",
                        "encoded-password",
                        "팔로잉" + suffix
                )
        );
        jdbcTemplate.update(
                "INSERT INTO follow (follower_id, following_id) VALUES (?, ?)",
                follower.getId(),
                following.getId()
        );

        assertThat(memberRepository.existsFollow(
                follower.getId(),
                following.getId()
        )).isTrue();
        assertThat(memberRepository.existsFollow(
                following.getId(),
                follower.getId()
        )).isFalse();
    }
}
