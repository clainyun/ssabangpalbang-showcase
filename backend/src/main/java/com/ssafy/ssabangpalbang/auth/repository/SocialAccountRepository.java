package com.ssafy.ssabangpalbang.auth.repository;

import com.ssafy.ssabangpalbang.auth.domain.SocialAccount;
import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SocialAccountRepository
        extends JpaRepository<SocialAccount, Long> {

    boolean existsByProviderAndSocialUserId(
            SocialProvider provider,
            String socialUserId
    );

    @Query("""
            select new com.ssafy.ssabangpalbang.auth.repository.SocialMemberSnapshot(
                member.id,
                member.email,
                member.nickname,
                member.profileImageUrl,
                member.selectedCharacterId,
                member.status
            )
            from SocialAccount socialAccount
            join socialAccount.member member
            where socialAccount.provider = :provider
              and socialAccount.socialUserId = :socialUserId
            """)
    Optional<SocialMemberSnapshot> findMemberSnapshot(
            @Param("provider") SocialProvider provider,
            @Param("socialUserId") String socialUserId
    );
}
