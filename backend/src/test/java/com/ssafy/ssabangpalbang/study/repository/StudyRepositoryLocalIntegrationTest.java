package com.ssafy.ssabangpalbang.study.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_INFRA_TESTS", matches = "true")
class StudyRepositoryLocalIntegrationTest {

    @Autowired
    private StudyRepository studyRepository;

    @Test
    void allRecruitingStudySortQueriesExecuteOnPostgreSQL() {
        PageRequest pageable = PageRequest.of(0, 20);
        Long absentApartmentId = Long.MAX_VALUE;

        assertThatCode(() -> studyRepository.findRecruitingOrderByScheduleAsc(
                absentApartmentId, pageable)).doesNotThrowAnyException();
        assertThatCode(() -> studyRepository.findRecruitingOrderByCreatedDesc(
                absentApartmentId, pageable)).doesNotThrowAnyException();
        assertThatCode(() -> studyRepository.findRecruitingOrderByRemainingCapacityDesc(
                absentApartmentId, pageable)).doesNotThrowAnyException();
    }
}
