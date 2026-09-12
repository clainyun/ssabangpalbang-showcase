package com.ssafy.ssabangpalbang.study.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudyDetailQueryRepository {
    private final EntityManager entityManager;

    public long countUnreadChat(Long studyId, Long memberId) {
        return ((Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM chat_message cm
                WHERE cm.study_id = :studyId
                  AND cm.sender_id IS DISTINCT FROM :memberId
                  AND cm.created_at > COALESCE(
                    (SELECT crs.last_read_at FROM chat_read_status crs
                     WHERE crs.study_id = :studyId AND crs.member_id = :memberId),
                    TIMESTAMPTZ '-infinity')
                """)
                .setParameter("studyId", studyId)
                .setParameter("memberId", memberId)
                .getSingleResult()).longValue();
    }

    public Optional<String> findFieldSessionStatus(Long studyId) {
        List<?> result = entityManager.createNativeQuery(
                        "SELECT fs.status FROM field_session fs WHERE fs.study_id = :studyId")
                .setParameter("studyId", studyId)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of((String) result.get(0));
    }

    public Optional<FieldSessionReportRow> findFieldSessionReport(Long studyId) {
        List<?> result = entityManager.createNativeQuery("""
                        SELECT fs.id,
                               fs.status,
                               r.id,
                               r.status
                        FROM field_session fs
                        LEFT JOIN report r ON r.field_session_id = fs.id
                        WHERE fs.study_id = :studyId
                        """)
                .setParameter("studyId", studyId)
                .getResultList();
        if (result.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) result.get(0);
        return Optional.of(new FieldSessionReportRow(
                ((Number) row[0]).longValue(),
                (String) row[1],
                row[2] == null ? null : ((Number) row[2]).longValue(),
                row[3] == null ? null : (String) row[3]
        ));
    }

    public record FieldSessionReportRow(
            Long fieldSessionId,
            String fieldVisitStatus,
            Long reportId,
            String reportStatus
    ) {
    }
}
