package com.ssafy.ssabangpalbang.study.repository;

import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Sql(statements = {
        "DROP TABLE IF EXISTS study_notice",
        "CREATE TABLE study_notice ("
                + "id BIGINT PRIMARY KEY, "
                + "study_id BIGINT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "deleted_at TIMESTAMP WITH TIME ZONE)"
})
@Sql(
        statements = "DROP TABLE IF EXISTS study_notice",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class StudyNoticeRepositoryTest {

    @Autowired
    private StudyNoticeRepository studyNoticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 목록과_활성_단건_조회에서_삭제된_공지를_제외한다() {
        insertNotice(18L, 10L, "최신 공지", false);
        insertNotice(17L, 10L, "삭제 공지", true);
        insertNotice(16L, 10L, "이전 공지", false);
        insertNotice(15L, 11L, "다른 스터디", false);

        List<StudyNotice> firstPage = studyNoticeRepository
                .findByStudyIdAndDeletedAtIsNullOrderByIdDesc(
                        10L, PageRequest.of(0, 10));
        List<StudyNotice> cursorPage = studyNoticeRepository
                .findByStudyIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        10L, 18L, PageRequest.of(0, 10));

        assertThat(firstPage).extracting(StudyNotice::getId)
                .containsExactly(18L, 16L);
        assertThat(cursorPage).extracting(StudyNotice::getId)
                .containsExactly(16L);
        assertThat(studyNoticeRepository.findByIdAndDeletedAtIsNull(17L)).isEmpty();
        assertThat(studyNoticeRepository.findById(17L)).isPresent();
    }

    private void insertNotice(Long id, Long studyId, String content, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO study_notice "
                        + "(id, study_id, content, created_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                        + "CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)",
                id,
                studyId,
                content,
                deleted
        );
    }
}
