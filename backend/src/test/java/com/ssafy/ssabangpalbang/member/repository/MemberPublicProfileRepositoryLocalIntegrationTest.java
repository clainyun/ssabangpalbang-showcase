package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.report.repository.PublicProfileReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.repository.PublicProfileStudyRow;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
class MemberPublicProfileRepositoryLocalIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 다른_사용자의_스터디_리포트_팔로잉을_공개_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member viewer = saveMember("viewer", suffix);
        Member target = saveMember("target", suffix);
        Member followed = saveMember("followed", suffix);
        Long apartmentId = insertApartment(suffix);
        Long activeStudyId = insertStudy(
                apartmentId,
                followed.getId(),
                "진행 중 스터디 " + suffix,
                "IN_PROGRESS"
        );
        Long completedStudyId = insertStudy(
                apartmentId,
                followed.getId(),
                "완료 스터디 " + suffix,
                "COMPLETED"
        );

        insertStudyMember(activeStudyId, target.getId(), "ACTIVE");
        insertStudyMember(completedStudyId, target.getId(), "REMOVED");
        jdbcTemplate.update(
                "INSERT INTO follow (follower_id, following_id) "
                        + "VALUES (?, ?), (?, ?)",
                target.getId(),
                followed.getId(),
                viewer.getId(),
                followed.getId()
        );
        jdbcTemplate.update(
                "INSERT INTO report "
                        + "(study_id, apartment_id, status, "
                        + "result_json, completed_at) "
                        + "VALUES (?, ?, 'DONE', "
                        + "CAST(? AS jsonb), now())",
                completedStudyId,
                apartmentId,
                "{\"title\":\"공개 리포트\"}"
        );

        Page<PublicProfileStudyRow> activeStudies = studyRepository
                .findPublicProfileStudies(
                        target.getId(),
                        "ACTIVE",
                        PageRequest.of(0, 20)
                );
        Page<PublicProfileStudyRow> allStudies = studyRepository
                .findPublicProfileStudies(
                        target.getId(),
                        "ALL",
                        PageRequest.of(0, 20)
                );
        Page<PublicProfileReportRow> reports = reportRepository
                .findPublicProfileReports(
                        viewer.getId(),
                        target.getId(),
                        PageRequest.of(0, 20)
                );
        Page<PublicProfileFollowingRow> followings = memberRepository
                .findPublicProfileFollowings(
                        viewer.getId(),
                        target.getId(),
                        PageRequest.of(0, 20)
                );

        assertThat(activeStudies.getContent())
                .extracting(PublicProfileStudyRow::getStudyId)
                .containsExactly(activeStudyId);
        assertThat(allStudies.getContent())
                .extracting(PublicProfileStudyRow::getStudyId)
                .containsExactly(activeStudyId, completedStudyId);
        assertThat(studyRepository.countPublicProfileStudies(target.getId()))
                .isEqualTo(2L);

        assertThat(reports.getContent()).hasSize(1);
        assertThat(reports.getContent().get(0).getStudyId())
                .isEqualTo(completedStudyId);
        assertThat(reports.getContent().get(0).getResultJson())
                .contains("공개 리포트");
        assertThat(reportRepository.countPublicProfileReports(target.getId()))
                .isEqualTo(1L);

        assertThat(followings.getContent()).hasSize(1);
        assertThat(followings.getContent().get(0).getMemberId())
                .isEqualTo(followed.getId());
        assertThat(followings.getContent().get(0).getIsFollowing()).isTrue();
        assertThat(memberRepository.countPublicProfileFollowings(
                target.getId()
        )).isEqualTo(1L);
    }

    private Member saveMember(String prefix, String suffix) {
        return memberRepository.saveAndFlush(new Member(
                prefix + "-" + suffix + "@example.com",
                "encoded-password",
                prefix + suffix
        ));
    }

    private Long insertApartment(String suffix) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO apartment "
                        + "(complex_code, name, longitude, latitude) "
                        + "VALUES (?, ?, 127.0, 37.5) RETURNING id",
                Long.class,
                "complex-" + suffix,
                "테스트 아파트 " + suffix
        );
    }

    private Long insertStudy(
            Long apartmentId,
            Long leaderId,
            String title,
            String status
    ) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO study "
                        + "(apartment_id, leader_id, title, goal, capacity, "
                        + "status) VALUES (?, ?, ?, '테스트 목표', 4, ?) "
                        + "RETURNING id",
                Long.class,
                apartmentId,
                leaderId,
                title,
                status
        );
    }

    private void insertStudyMember(
            Long studyId,
            Long memberId,
            String status
    ) {
        jdbcTemplate.update(
                "INSERT INTO study_member "
                        + "(study_id, member_id, role, status) "
                        + "VALUES (?, ?, 'MEMBER', ?)",
                studyId,
                memberId,
                status
        );
    }
}
