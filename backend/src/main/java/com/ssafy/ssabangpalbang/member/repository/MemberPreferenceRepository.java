package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberPreferenceRepository
        extends JpaRepository<MemberPreference, Long> {

    Optional<MemberPreference> findByMemberId(Long memberId);
}
