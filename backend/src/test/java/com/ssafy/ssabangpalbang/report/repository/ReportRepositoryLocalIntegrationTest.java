package com.ssafy.ssabangpalbang.report.repository;

import com.ssafy.ssabangpalbang.report.domain.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class ReportRepositoryLocalIntegrationTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 스터디장과_ACTIVE_스터디원의_DONE_리포트를_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long leaderId = insertMember("leader-" + suffix);
        Long activeMemberId = insertMember("member-" + suffix);
        Long outsiderId = insertMember("outsider-" + suffix);
        Long apartmentId = insertApartment(suffix);
        Long studyId = insertStudy(apartmentId, leaderId, suffix);

        jdbcTemplate.update(
                """
                        INSERT INTO study_member
                            (study_id, member_id, role, status)
                        VALUES (?, ?, 'MEMBER', 'ACTIVE')
                        """,
                studyId,
                activeMemberId
        );
        Long sessionId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO field_session (study_id, status)
                        VALUES (?, 'ENDED')
                        RETURNING id
                        """,
                Long.class,
                studyId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO field_participant
                            (session_id, member_id, status)
                        VALUES (?, ?, 'ENDED')
                        """,
                sessionId,
                leaderId
        );
        Long reportId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO report (
                            study_id, apartment_id, status,
                            result_json, completed_at
                        )
                        VALUES (
                            ?, ?, 'DONE',
                            CAST(? AS jsonb), ?
                        )
                        RETURNING id
                        """,
                Long.class,
                studyId,
                apartmentId,
                """
                        {"title":"통합 검증 리포트","summary":"요약","analysisTags":["교통"]}
                        """,
                OffsetDateTime.parse("2026-07-22T18:07:00+09:00")
        );
        jdbcTemplate.update(
                """
                        INSERT INTO report_favorite (member_id, report_id)
                        VALUES (?, ?)
                        """,
                activeMemberId,
                reportId
        );

        Page<MemberReportRow> leaderReports = reportRepository
                .findAccessibleDoneReports(
                        leaderId,
                        PageRequest.of(0, 20)
                );
        Page<MemberReportRow> activeMemberReports = reportRepository
                .findAccessibleDoneReports(
                        activeMemberId,
                        PageRequest.of(0, 20)
                );
        Page<MemberReportRow> outsiderReports = reportRepository
                .findAccessibleDoneReports(
                        outsiderId,
                        PageRequest.of(0, 20)
                );

        MemberReportRow leaderReport = leaderReports.stream()
                .filter(row -> row.getReportId().equals(reportId))
                .findFirst()
                .orElseThrow();
        MemberReportRow activeMemberReport = activeMemberReports.stream()
                .filter(row -> row.getReportId().equals(reportId))
                .findFirst()
                .orElseThrow();

        assertThat(leaderReport.getCanViewEvidence()).isTrue();
        assertThat(leaderReport.getParticipantCount()).isEqualTo(1L);
        assertThat(leaderReport.getResultJson()).contains("통합 검증 리포트");
        assertThat(activeMemberReport.getCanViewEvidence()).isFalse();
        assertThat(activeMemberReport.getFavoritedByMe()).isTrue();
        assertThat(outsiderReports.stream()
                .noneMatch(row -> row.getReportId().equals(reportId)))
                .isTrue();
    }

    @Test
    void 찜한_DONE_리포트만_최근_찜한_순서로_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Long memberId = insertMember("favorite-" + suffix);
        Long leaderId = insertMember("favorite-leader-" + suffix);
        Long apartmentId = insertApartment(suffix);
        jdbcTemplate.update(
                "UPDATE apartment SET address = ? WHERE id = ?",
                "서울특별시 성동구 매봉길 15",
                apartmentId
        );

        Long oldStudyId = insertStudy(
                apartmentId,
                leaderId,
                "old-" + suffix
        );
        Long newStudyId = insertStudy(
                apartmentId,
                leaderId,
                "new-" + suffix
        );
        Long pendingStudyId = insertStudy(
                apartmentId,
                leaderId,
                "pending-" + suffix
        );
        Long canceledStudyId = insertStudy(
                apartmentId,
                leaderId,
                "canceled-" + suffix
        );
        jdbcTemplate.update(
                "UPDATE study SET status = 'CANCELED' WHERE id = ?",
                canceledStudyId
        );

        Long oldReportId = insertReport(
                oldStudyId,
                apartmentId,
                "DONE",
                "오래된 리포트"
        );
        Long newReportId = insertReport(
                newStudyId,
                apartmentId,
                "DONE",
                "최근 리포트"
        );
        Long pendingReportId = insertReport(
                pendingStudyId,
                apartmentId,
                "PENDING",
                "진행 중 리포트"
        );
        Long canceledReportId = insertReport(
                canceledStudyId,
                apartmentId,
                "DONE",
                "취소 스터디 리포트"
        );

        insertFavorite(
                memberId,
                oldReportId,
                "2026-07-23T09:00:00+09:00"
        );
        insertFavorite(
                memberId,
                newReportId,
                "2026-07-24T09:00:00+09:00"
        );
        insertFavorite(
                memberId,
                pendingReportId,
                "2026-07-25T09:00:00+09:00"
        );
        insertFavorite(
                memberId,
                canceledReportId,
                "2026-07-26T09:00:00+09:00"
        );

        Page<FavoriteReportRow> reports = reportRepository
                .findFavoriteDoneReports(
                        memberId,
                        PageRequest.of(0, 20)
                );

        assertThat(reports.getTotalElements()).isEqualTo(2);
        assertThat(reports.getContent())
                .extracting(FavoriteReportRow::getReportId)
                .containsExactly(newReportId, oldReportId);
        assertThat(reports.getContent().get(0).getResultJson())
                .contains("최근 리포트");
        assertThat(reports.getContent().get(0).getApartmentAddress())
                .isEqualTo("서울특별시 성동구 매봉길 15");
    }

    @Test
    void DONE이_아닌_리포트만_있으면_조회하지_않는다() {
        String suffix = uniqueSuffix();
        Long leaderId = insertMember("status-leader-" + suffix);
        Long apartmentId = insertApartment("status-" + suffix);

        for (String status : List.of("PENDING", "IN_PROGRESS", "FAILED")) {
            Long studyId = insertStudy(
                    apartmentId,
                    leaderId,
                    status.toLowerCase() + "-" + suffix
            );
            insertReport(studyId, apartmentId, status, status);
        }

        List<Report> reports = reportRepository.findDoneByApartmentId(
                apartmentId,
                PageRequest.of(0, 1)
        );

        assertThat(reports).isEmpty();
    }

    @Test
    void DONE_리포트는_완료_시각이_최근인_한_건만_조회한다() {
        String suffix = uniqueSuffix();
        Long leaderId = insertMember("recent-leader-" + suffix);
        Long apartmentId = insertApartment("recent-" + suffix);
        Long oldStudyId = insertStudy(
                apartmentId,
                leaderId,
                "recent-old-" + suffix
        );
        Long latestStudyId = insertStudy(
                apartmentId,
                leaderId,
                "recent-latest-" + suffix
        );
        insertReportAt(
                oldStudyId,
                apartmentId,
                "DONE",
                "오래된 리포트",
                "2026-07-21T18:07:00+09:00"
        );
        Long latestReportId = insertReportAt(
                latestStudyId,
                apartmentId,
                "DONE",
                "최근 리포트",
                "2026-07-22T18:07:00+09:00"
        );

        List<Report> reports = reportRepository.findDoneByApartmentId(
                apartmentId,
                PageRequest.of(0, 1)
        );

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getId()).isEqualTo(latestReportId);
        assertThat(reports.get(0).getCompletedAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-07-22T18:07:00+09:00"
                ).toInstant());
    }

    @Test
    void 삭제되거나_취소된_스터디의_DONE_리포트는_조회하지_않는다() {
        String suffix = uniqueSuffix();
        Long leaderId = insertMember("excluded-leader-" + suffix);
        Long apartmentId = insertApartment("excluded-" + suffix);
        Long deletedStudyId = insertStudy(
                apartmentId,
                leaderId,
                "deleted-" + suffix
        );
        Long canceledStudyId = insertStudy(
                apartmentId,
                leaderId,
                "canceled-" + suffix
        );
        jdbcTemplate.update(
                "UPDATE study SET deleted_at = now() WHERE id = ?",
                deletedStudyId
        );
        jdbcTemplate.update(
                "UPDATE study SET status = 'CANCELED' WHERE id = ?",
                canceledStudyId
        );
        insertReport(
                deletedStudyId,
                apartmentId,
                "DONE",
                "삭제 스터디 리포트"
        );
        insertReport(
                canceledStudyId,
                apartmentId,
                "DONE",
                "취소 스터디 리포트"
        );

        List<Report> reports = reportRepository.findDoneByApartmentId(
                apartmentId,
                PageRequest.of(0, 10)
        );

        assertThat(reports).isEmpty();
    }

    private Long insertMember(String unique) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO member (
                            email, password_hash, nickname, status
                        )
                        VALUES (?, 'encoded-password', ?, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                unique + "@example.com",
                unique
        );
    }

    private Long insertApartment(String suffix) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO apartment (
                            complex_code, name, longitude, latitude
                        )
                        VALUES (?, ?, 127.0, 37.5)
                        RETURNING id
                        """,
                Long.class,
                "report-" + suffix,
                "리포트아파트" + suffix
        );
    }

    private Long insertStudy(
            Long apartmentId,
            Long leaderId,
            String suffix
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO study (
                            apartment_id, leader_id, title, goal,
                            capacity, status
                        )
                        VALUES (?, ?, ?, '리포트 검증', 4, 'COMPLETED')
                        RETURNING id
                        """,
                Long.class,
                apartmentId,
                leaderId,
                "리포트스터디" + suffix
        );
    }

    private Long insertReport(
            Long studyId,
            Long apartmentId,
            String status,
            String title
    ) {
        return insertReportAt(
                studyId,
                apartmentId,
                status,
                title,
                "2026-07-22T18:07:00+09:00"
        );
    }

    private Long insertReportAt(
            Long studyId,
            Long apartmentId,
            String status,
            String title,
            String completedAt
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO report (
                            study_id, apartment_id, status,
                            result_json, completed_at
                        )
                        VALUES (
                            ?, ?, ?,
                            CAST(? AS jsonb), CAST(? AS timestamptz)
                        )
                        RETURNING id
                        """,
                Long.class,
                studyId,
                apartmentId,
                status,
                "{\"title\":\"" + title + "\"}",
                completedAt
        );
    }

    private String uniqueSuffix() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }

    private void insertFavorite(
            Long memberId,
            Long reportId,
            String createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO report_favorite (
                            member_id, report_id, created_at
                        )
                        VALUES (?, ?, CAST(? AS timestamptz))
                        """,
                memberId,
                reportId,
                createdAt
        );
    }
}
