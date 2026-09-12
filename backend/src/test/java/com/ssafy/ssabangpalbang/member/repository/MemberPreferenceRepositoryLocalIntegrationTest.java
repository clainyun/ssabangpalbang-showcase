package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
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
class MemberPreferenceRepositoryLocalIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void PostgreSQL_JSONB에_우선순위_순서를_저장하고_갱신한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member member = memberRepository.saveAndFlush(new Member(
                "onboarding-" + suffix + "@example.com",
                "encoded-password",
                "온보딩" + suffix
        ));
        MemberPreference preference = new MemberPreference(member.getId());
        member.updateOnboardingProfile(
                "SIXTIES_PLUS",
                false,
                "PALBANG_DOG"
        );
        preference.update(
                "RESIDENCE",
                "NEWLYWED",
                "MARRIED",
                true,
                true,
                "500M_TO_700M",
                "서울특별시 송파구",
                List.of("TRANSPORT", "WALKABILITY", "PARKING")
        );

        memberPreferenceRepository.saveAndFlush(preference);
        entityManager.clear();

        MemberPreference savedPreference = memberPreferenceRepository
                .findByMemberId(member.getId())
                .orElseThrow();
        assertThat(savedPreference.getPriorities()).containsExactly(
                "TRANSPORT",
                "WALKABILITY",
                "PARKING"
        );
        assertThat(savedPreference.getMaritalStatus()).isEqualTo("MARRIED");
        assertThat(savedPreference.getHasVehicle()).isTrue();
        assertThat(savedPreference.getHasChildren()).isTrue();
        assertThat(savedPreference.getCreatedAt()).isNotNull();
        assertThat(savedPreference.getUpdatedAt()).isNotNull();
        assertThat(memberRepository.existsPreferenceByMemberId(
                member.getId()
        )).isTrue();
        Member updatedMember = memberRepository.findById(member.getId())
                .orElseThrow();
        assertThat(updatedMember.getAgeGroup()).isEqualTo("SIXTIES_PLUS");
        assertThat(updatedMember.getSelectedCharacterId())
                .isEqualTo("PALBANG_DOG");

        savedPreference.update(
                "INVESTMENT",
                "SINGLE",
                "SINGLE",
                false,
                false,
                "700M_PLUS",
                "서울특별시 강남구",
                List.of("COMMERCIAL", "TRANSPORT")
        );
        memberPreferenceRepository.saveAndFlush(savedPreference);
        entityManager.clear();

        MemberPreference updatedPreference = memberPreferenceRepository
                .findByMemberId(member.getId())
                .orElseThrow();
        assertThat(updatedPreference.getPurpose()).isEqualTo("INVESTMENT");
        assertThat(updatedPreference.getMaritalStatus()).isEqualTo("SINGLE");
        assertThat(updatedPreference.getHasVehicle()).isFalse();
        assertThat(updatedPreference.getHasChildren()).isFalse();
        assertThat(updatedPreference.getPriorities()).containsExactly(
                "COMMERCIAL",
                "TRANSPORT"
        );
    }
}
