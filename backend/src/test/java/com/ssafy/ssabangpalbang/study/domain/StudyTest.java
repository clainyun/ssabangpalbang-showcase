package com.ssafy.ssabangpalbang.study.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyTest {

    @Test
    void createsRecruitingStudyWithoutDeletionTime() {
        Study study = Study.create(
                15L, 7L, "옥수동 주말 임장", "소개", "목표",
                6, StudyPurpose.RESIDENCE
        );

        assertThat(study.getStatus()).isEqualTo(StudyStatus.RECRUITING);
        assertThat(study.getDeletedAt()).isNull();
    }

    @Test
    void createsLeaderAsActiveStudyMember() {
        StudyMember member = StudyMember.createLeader(10L, 7L);

        assertThat(member.getRole()).isEqualTo(StudyMemberRole.LEADER);
        assertThat(member.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
        assertThat(member.getMemberId()).isEqualTo(7L);
        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void closesRecruitmentWithDedicatedTimestamp() {
        Study study = Study.create(
                15L, 7L, "옥수동 주말 임장", "소개", "목표",
                6, StudyPurpose.RESIDENCE
        );
        Instant closedAt = Instant.parse("2026-07-30T01:00:00Z");

        study.closeRecruitment(closedAt);

        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
        assertThat(study.getRecruitmentClosedAt()).isEqualTo(closedAt);
    }

    @Test
    void 리포트가_완료되면_진행_중_스터디를_완료한다() {
        Study study = inProgressStudy();

        study.markCompleted();

        assertThat(study.getStatus()).isEqualTo(StudyStatus.COMPLETED);
    }

    @Test
    void 완료_이벤트가_중복되어도_완료_상태를_유지한다() {
        Study study = inProgressStudy();
        study.markCompleted();

        study.markCompleted();

        assertThat(study.getStatus()).isEqualTo(StudyStatus.COMPLETED);
    }

    @Test
    void 진행_중이_아닌_스터디를_완료_상태로_덮어쓰지_않는다() {
        Study recruiting = study();
        Study closed = study();
        closed.closeRecruitment(Instant.parse("2026-08-03T01:00:00Z"));
        Study canceled = study();
        canceled.cancel(Instant.parse("2026-08-03T01:00:00Z"));

        for (Study candidate : List.of(
                recruiting,
                closed,
                canceled
        )) {
            StudyStatus before = candidate.getStatus();
            assertThatThrownBy(candidate::markCompleted)
                    .isInstanceOf(IllegalStateException.class);
            assertThat(candidate.getStatus()).isEqualTo(before);
        }
    }

    @Test
    void reopensRecruitmentAndClearsClosedTimestamp() {
        Study study = study();
        study.closeRecruitment(Instant.parse("2026-08-03T01:00:00Z"));

        study.reopenRecruitment();

        assertThat(study.getStatus()).isEqualTo(StudyStatus.RECRUITING);
        assertThat(study.getRecruitmentClosedAt()).isNull();
    }

    private Study inProgressStudy() {
        Study study = study();
        study.closeRecruitment(Instant.parse("2026-08-03T01:00:00Z"));
        study.markInProgress();
        return study;
    }

    private Study study() {
        return Study.create(
                15L, 7L, "옥수동 주말 임장", "소개", "목표",
                6, StudyPurpose.RESIDENCE
        );
    }
}
