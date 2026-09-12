package com.ssafy.ssabangpalbang.study.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentStudySortTest {

    @Test
    void mapsAllSupportedSortsToSafeSqlOrderByClauses() {
        assertThat(ApartmentStudySort.SCHEDULE_ASC.orderByClause())
                .isEqualTo("sch.start_at ASC NULLS LAST, s.id ASC");
        assertThat(ApartmentStudySort.CREATED_DESC.orderByClause())
                .isEqualTo("s.created_at DESC, s.id DESC");
        assertThat(ApartmentStudySort.REMAINING_CAPACITY_DESC.orderByClause())
                .isEqualTo("(s.capacity - mc.cnt) DESC, s.id DESC");
    }

    @Test
    void scheduleSortUsesNullsLastAndStableSecondaryKey() {
        String clause = ApartmentStudySort.from(null).orderByClause();
        assertThat(clause).contains("NULLS LAST");
        assertThat(clause).endsWith("s.id ASC");
    }

    @Test
    void rejectsUnknownCaseSensitiveValuesWithAllowedValues() {
        for (String value : new String[]{"schedule_asc", "UNKNOWN", ""}) {
            assertThatThrownBy(() -> ApartmentStudySort.from(value))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.APARTMENT_STUDY_SORT_INVALID);
                        assertThat(exception.getData().get("allowedValues"))
                                .asList().containsExactly(
                                        "SCHEDULE_ASC", "CREATED_DESC",
                                        "REMAINING_CAPACITY_DESC");
                    });
        }
    }
}
