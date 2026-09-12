package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyNoticeRepository extends JpaRepository<StudyNotice, Long> {
    Optional<StudyNotice> findByIdAndDeletedAtIsNull(Long id);

    List<StudyNotice> findByStudyIdAndDeletedAtIsNullOrderByIdDesc(
            Long studyId, Pageable pageable);

    List<StudyNotice> findByStudyIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long studyId, Long id, Pageable pageable);
}
