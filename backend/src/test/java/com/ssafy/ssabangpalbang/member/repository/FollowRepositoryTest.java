package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Follow;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
class FollowRepositoryTest {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 지정한_정방향_팔로우_관계만_삭제한다() {
        followRepository.saveAndFlush(Follow.of(1L, 15L));
        Follow reverse = followRepository.saveAndFlush(Follow.of(15L, 1L));
        Follow other = followRepository.saveAndFlush(Follow.of(1L, 20L));

        int deletedCount = followRepository.deleteRelation(1L, 15L);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(followRepository.findById(reverse.getId())).isPresent();
        assertThat(followRepository.findById(other.getId())).isPresent();
        assertThat(followRepository.findByFollowerIdAndFollowingId(1L, 15L))
                .isEmpty();
    }

    @Test
    void 이미_없는_팔로우_관계_삭제는_0을_반환한다() {
        assertThat(followRepository.deleteRelation(1L, 15L)).isZero();
    }

    @Test
    void 활성_회원만_팔로우_ID_역순과_커서로_조회한다() {
        Member follower = saveMember("follower");
        Member first = saveMember("first");
        first.updateOnboardingProfile("TWENTIES", true, "PALBANG");
        Member second = saveMember("second");
        second.updateOnboardingProfile(
                "THIRTIES",
                false,
                "PALBANG_DOG"
        );
        Member withdrawn = saveMember("withdrawn");
        ReflectionTestUtils.setField(
                withdrawn,
                "status",
                MemberStatus.WITHDRAWN
        );
        memberRepository.saveAllAndFlush(List.of(first, second, withdrawn));

        Follow oldest = followRepository.saveAndFlush(
                Follow.of(follower.getId(), first.getId())
        );
        Follow newestActive = followRepository.saveAndFlush(
                Follow.of(follower.getId(), second.getId())
        );
        followRepository.saveAndFlush(
                Follow.of(follower.getId(), withdrawn.getId())
        );

        List<FollowingMemberRow> firstPage = followRepository
                .findFollowingPage(
                        follower.getId(),
                        null,
                        PageRequest.of(0, 3)
                );
        List<FollowingMemberRow> nextPage = followRepository
                .findFollowingPage(
                        follower.getId(),
                        newestActive.getId(),
                        PageRequest.of(0, 3)
                );

        assertThat(firstPage)
                .extracting(FollowingMemberRow::getFollowId)
                .containsExactly(newestActive.getId(), oldest.getId());
        assertThat(firstPage)
                .extracting(FollowingMemberRow::getMemberId)
                .containsExactly(second.getId(), first.getId());
        assertThat(firstPage.get(0).getAgeGroupPublicAgreed()).isFalse();
        assertThat(firstPage.get(1).getAgeGroupPublicAgreed()).isTrue();
        assertThat(firstPage)
                .extracting(FollowingMemberRow::getParticipatingStudyCount)
                .containsOnly(0L);
        assertThat(nextPage)
                .extracting(FollowingMemberRow::getFollowId)
                .containsExactly(oldest.getId());
        assertThat(memberRepository.countPublicProfileFollowings(
                follower.getId()
        )).isEqualTo(2L);
    }

    private Member saveMember(String prefix) {
        return memberRepository.saveAndFlush(new Member(
                prefix + "@example.com",
                "encoded-password",
                prefix
        ));
    }
}
