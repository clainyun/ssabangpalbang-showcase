package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByMemberId(Long memberId);

    List<FcmToken> findAllByMemberIdIn(Collection<Long> memberIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM FcmToken f
             WHERE f.deviceId = :deviceId
               AND f.memberId <> :memberId
            """)
    int deleteByDeviceIdAndOtherMember(
            @Param("deviceId") String deviceId,
            @Param("memberId") Long memberId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM FcmToken f
             WHERE f.token = :token
               AND NOT (f.memberId = :memberId AND f.deviceId = :deviceId)
            """)
    int deleteByTokenOnOtherOwner(
            @Param("token") String token,
            @Param("memberId") Long memberId,
            @Param("deviceId") String deviceId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM FcmToken f
             WHERE f.memberId = :memberId
               AND f.deviceId = :deviceId
            """)
    int deleteByMemberIdAndDeviceId(
            @Param("memberId") Long memberId,
            @Param("deviceId") String deviceId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM FcmToken f
             WHERE f.memberId = :memberId
            """)
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    Optional<FcmToken> findByMemberIdAndDeviceId(
            Long memberId,
            String deviceId
    );
}
