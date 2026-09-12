package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.repository.MemberStudyRow;
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
class MemberStudyRepositoryLocalIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 참여_관계_필터_정렬과_읽지_않은_채팅_수를_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        Member member = saveMember("member", suffix);
        Member leader = saveMember("leader", suffix);
        Long apartmentId = insertApartment(suffix);

        // 참여(study_member) 스터디는 sm.joined_at 이 정렬 키가 되고,
        // 리더 스터디는 sm 행이 없어 study.created_at 이 정렬 키가 된다.
        // created_at 은 삽입 순서(id 오름차순)와 어긋나게 부여해,
        // 새 정렬이 id 가 아니라 가입/생성 시각으로 결정됨을 검증한다.
        Long memberStudyId = insertStudy(
                apartmentId,
                leader.getId(),
                "참여 스터디 " + suffix,
                "CLOSED",
                false,
                "2026-07-01T00:00:00Z"
        );
        Long leaderStudyId = insertStudy(
                apartmentId,
                member.getId(),
                "리더 스터디 " + suffix,
                "RECRUITING",
                false,
                "2026-07-20T00:00:00Z"
        );
        Long completedStudyId = insertStudy(
                apartmentId,
                leader.getId(),
                "완료 스터디 " + suffix,
                "COMPLETED",
                false,
                "2026-07-02T00:00:00Z"
        );
        Long inProgressStudyId = insertStudy(
                apartmentId,
                member.getId(),
                "진행중 스터디 " + suffix,
                "IN_PROGRESS",
                false,
                "2026-07-15T00:00:00Z"
        );
        Long removedStudyId = insertStudy(
                apartmentId,
                leader.getId(),
                "제외 스터디 " + suffix,
                "IN_PROGRESS",
                false,
                "2026-07-30T00:00:00Z"
        );
        insertStudy(
                apartmentId,
                member.getId(),
                "취소 스터디 " + suffix,
                "CANCELED",
                false,
                "2026-07-31T00:00:00Z"
        );
        insertStudy(
                apartmentId,
                member.getId(),
                "삭제 스터디 " + suffix,
                "IN_PROGRESS",
                true,
                "2026-07-31T00:00:00Z"
        );

        insertStudyMember(
                memberStudyId,
                member.getId(),
                "ACTIVE",
                "2026-07-25T00:00:00Z"
        );
        insertStudyMember(
                completedStudyId,
                member.getId(),
                "ACTIVE",
                "2026-07-05T00:00:00Z"
        );
        insertStudyMember(
                removedStudyId,
                member.getId(),
                "REMOVED",
                "2026-07-30T00:00:00Z"
        );
        insertSchedule(
                memberStudyId,
                "2026-08-01T01:00:00Z",
                "옥수역 3번 출구"
        );
        insertSchedule(
                leaderStudyId,
                "2026-08-02T01:00:00Z",
                "서울숲역 1번 출구"
        );
        insertReadStatus(
                memberStudyId,
                member.getId(),
                "2026-07-29T00:00:00Z"
        );
        insertChatMessage(
                memberStudyId,
                leader.getId(),
                "2026-07-28T23:59:00Z"
        );
        insertChatMessage(
                memberStudyId,
                member.getId(),
                "2026-07-29T00:01:00Z"
        );
        insertChatMessage(
                memberStudyId,
                leader.getId(),
                "2026-07-29T00:02:00Z"
        );
        insertChatMessage(
                memberStudyId,
                null,
                "2026-07-29T00:03:00Z"
        );

        Page<MemberStudyRow> active = studyRepository.findMemberStudies(
                member.getId(),
                "ACTIVE",
                PageRequest.of(0, 20)
        );
        Page<MemberStudyRow> completed = studyRepository.findMemberStudies(
                member.getId(),
                "COMPLETED",
                PageRequest.of(0, 20)
        );
        Page<MemberStudyRow> inProgress = studyRepository.findMemberStudies(
                member.getId(),
                "IN_PROGRESS",
                PageRequest.of(0, 20)
        );
        Page<MemberStudyRow> all = studyRepository.findMemberStudies(
                member.getId(),
                "ALL",
                PageRequest.of(0, 20)
        );

        assertThat(active.getContent())
                .extracting(MemberStudyRow::getStudyId)
                .containsExactly(
                        memberStudyId,
                        leaderStudyId,
                        inProgressStudyId
                );
        assertThat(active.getContent().get(0).getRole())
                .isEqualTo("MEMBER");
        assertThat(active.getContent().get(1).getRole())
                .isEqualTo("LEADER");
        assertThat(active.getContent().get(0).getUnreadChatCount())
                .isEqualTo(2L);
        assertThat(completed.getContent())
                .extracting(MemberStudyRow::getStudyId)
                .containsExactly(completedStudyId);
        assertThat(inProgress.getContent())
                .extracting(MemberStudyRow::getStudyId)
                .containsExactly(inProgressStudyId);
        assertThat(all.getContent())
                .extracting(MemberStudyRow::getStudyId)
                .containsExactly(
                        memberStudyId,
                        leaderStudyId,
                        inProgressStudyId,
                        completedStudyId
                );
        assertThat(all.getTotalElements()).isEqualTo(4);
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
                "member-study-" + suffix,
                "테스트 아파트 " + suffix
        );
    }

    private Long insertStudy(
            Long apartmentId,
            Long leaderId,
            String title,
            String status,
            boolean deleted,
            String createdAt
    ) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO study "
                        + "(apartment_id, leader_id, title, intro, goal, "
                        + "capacity, status, deleted_at, created_at) "
                        + "VALUES (?, ?, ?, '테스트 소개', '테스트 목표', "
                        + "4, ?, CASE WHEN ? THEN now() ELSE NULL END, "
                        + "CAST(? AS TIMESTAMPTZ)) "
                        + "RETURNING id",
                Long.class,
                apartmentId,
                leaderId,
                title,
                status,
                deleted,
                createdAt
        );
    }

    private void insertStudyMember(
            Long studyId,
            Long memberId,
            String status,
            String joinedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO study_member "
                        + "(study_id, member_id, role, status, joined_at) "
                        + "VALUES (?, ?, 'MEMBER', ?, CAST(? AS TIMESTAMPTZ))",
                studyId,
                memberId,
                status,
                joinedAt
        );
    }

    private void insertSchedule(
            Long studyId,
            String startAt,
            String meetingPlace
    ) {
        jdbcTemplate.update(
                "INSERT INTO schedule "
                        + "(study_id, start_at, meeting_place) "
                        + "VALUES (?, CAST(? AS TIMESTAMPTZ), ?)",
                studyId,
                startAt,
                meetingPlace
        );
    }

    private void insertReadStatus(
            Long studyId,
            Long memberId,
            String lastReadAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO chat_read_status "
                        + "(study_id, member_id, last_read_at) "
                        + "VALUES (?, ?, CAST(? AS TIMESTAMPTZ))",
                studyId,
                memberId,
                lastReadAt
        );
    }

    private void insertChatMessage(
            Long studyId,
            Long senderId,
            String createdAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO chat_message "
                        + "(study_id, sender_id, message_type, content, "
                        + "created_at) VALUES (?, ?, 'TEXT', '테스트', "
                        + "CAST(? AS TIMESTAMPTZ))",
                studyId,
                senderId,
                createdAt
        );
    }
}
