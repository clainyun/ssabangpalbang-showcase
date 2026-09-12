package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.StudyApplication;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface StudyApplicationRepository extends JpaRepository<StudyApplication, Long> {
    Optional<StudyApplication> findByStudyIdAndApplicantId(Long studyId, Long applicantId);
    List<StudyApplication> findByStudyIdInAndApplicantId(
            Collection<Long> studyIds, Long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sa
            FROM StudyApplication sa
            WHERE sa.applicantId = :applicantId
              AND sa.status = :status
            ORDER BY sa.id ASC
            """)
    List<StudyApplication> findAllForUpdateByApplicantIdAndStatus(
            @Param("applicantId") Long applicantId,
            @Param("status") StudyApplicationStatus status
    );

    @Query("""
            SELECT sa FROM StudyApplication sa
            WHERE sa.studyId = :studyId
              AND (:status IS NULL OR sa.status = :status)
              AND (:cursor IS NULL OR sa.id < :cursor)
            ORDER BY sa.id DESC
            """)
    List<StudyApplication> findPage(
            @Param("studyId") Long studyId,
            @Param("status") StudyApplicationStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    long countByStudyIdAndStatus(Long studyId, StudyApplicationStatus status);
    List<StudyApplication> findByStudyIdAndStatus(Long studyId, StudyApplicationStatus status);
}
