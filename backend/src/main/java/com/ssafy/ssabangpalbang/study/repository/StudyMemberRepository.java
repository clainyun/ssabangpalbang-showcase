package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {
    Optional<StudyMember> findByStudyIdAndMemberId(Long studyId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sm
            FROM StudyMember sm
            WHERE sm.studyId = :studyId
              AND sm.memberId = :memberId
            """)
    Optional<StudyMember> findForUpdateByStudyIdAndMemberId(
            @Param("studyId") Long studyId,
            @Param("memberId") Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sm
            FROM StudyMember sm
            WHERE sm.memberId = :memberId
              AND sm.role = :role
              AND sm.status = :status
            ORDER BY sm.id ASC
            """)
    List<StudyMember> findAllForUpdateByMemberIdAndRoleAndStatus(
            @Param("memberId") Long memberId,
            @Param("role") StudyMemberRole role,
            @Param("status") StudyMemberStatus status
    );

    long countByStudyIdAndStatus(Long studyId, StudyMemberStatus status);
    List<StudyMember> findByStudyIdAndStatus(Long studyId, StudyMemberStatus status);
    List<StudyMember> findByStudyIdInAndMemberIdAndStatus(
            Collection<Long> studyIds, Long memberId, StudyMemberStatus status);
}
