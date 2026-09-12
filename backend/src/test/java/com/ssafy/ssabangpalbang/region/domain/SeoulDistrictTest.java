package com.ssafy.ssabangpalbang.region.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulDistrictTest {

    @Test
    void 서울_자치구는_가나다순으로_중복없이_25개다() {
        var districts = Arrays.asList(SeoulDistrict.values());

        assertThat(districts).hasSize(25);
        assertThat(districts)
                .extracting(SeoulDistrict::getDistrictName)
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(districts)
                .extracting(SeoulDistrict::getCode)
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("\\d{5}") && code.startsWith("11"));
    }

    @Test
    void 코드로_자치구를_조회한다() {
        assertThat(SeoulDistrict.findByCode("11710"))
                .contains(SeoulDistrict.SONGPA);
        assertThat(SeoulDistrict.findByCode("11999")).isEmpty();
    }
}
