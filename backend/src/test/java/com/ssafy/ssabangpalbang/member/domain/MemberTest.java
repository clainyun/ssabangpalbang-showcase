package com.ssafy.ssabangpalbang.member.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void 회원가입_기본값을_적용하고_이메일을_정규화한다() {
        Member member = new Member(
                "  Dain@Example.COM ",
                "encoded-password",
                "루돌푸"
        );

        assertThat(member.getEmail()).isEqualTo("dain@example.com");
        assertThat(member.getSelectedCharacterId()).isEqualTo("PALBANG");
        assertThat(member.isAgeGroupPublicAgreed()).isFalse();
        assertThat(member.isServiceNotificationAgreed()).isTrue();
        assertThat(member.isAdNotificationAgreed()).isFalse();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getDeletedAt()).isNull();
    }

    @Test
    void 온보딩_연령대와_선택_캐릭터를_갱신한다() {
        Member member = new Member(
                "dain@example.com",
                "encoded-password",
                "루돌푸"
        );

        member.updateOnboardingProfile(
                "SIXTIES_PLUS",
                true,
                "PALBANG_DOG"
        );

        assertThat(member.getAgeGroup()).isEqualTo("SIXTIES_PLUS");
        assertThat(member.isAgeGroupPublicAgreed()).isTrue();
        assertThat(member.getSelectedCharacterId())
                .isEqualTo("PALBANG_DOG");
    }

    @Test
    void 알림_수신_설정은_포함된_필드만_독립적으로_갱신한다() {
        Member member = new Member(
                "dain@example.com",
                "encoded-password",
                "다인"
        );

        member.updateNotificationSettings(false, null);

        assertThat(member.isServiceNotificationAgreed()).isFalse();
        assertThat(member.isAdNotificationAgreed()).isFalse();

        member.updateNotificationSettings(null, true);

        assertThat(member.isServiceNotificationAgreed()).isFalse();
        assertThat(member.isAdNotificationAgreed()).isTrue();
    }

    @Test
    void 회원을_탈퇴_상태로_변경하고_처리_시각을_저장한다() {
        Member member = new Member(
                "dain@example.com",
                "encoded-password",
                "다인"
        );
        Instant withdrawnAt = Instant.parse("2026-07-30T06:45:00Z");

        member.withdraw(withdrawnAt);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getDeletedAt()).isEqualTo(withdrawnAt);
    }

    @Test
    void 회원을_JSON으로_직렬화해도_비밀번호_해시는_노출하지_않는다() throws Exception {
        Member member = new Member(
                "dain@example.com",
                "encoded-password-secret",
                "다인"
        );

        JsonNode json = new ObjectMapper().valueToTree(member);

        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.toString()).doesNotContain("encoded-password-secret");
    }
}
