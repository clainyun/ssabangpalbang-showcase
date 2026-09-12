package com.ssafy.ssabangpalbang.apartment.dataload.support;

import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptTradeItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFieldMapperTest {

    @Test
    void mapsPrice() {
        assertThat(TransactionFieldMapper.price("235,000")).isEqualTo(235000L);
        assertThat(TransactionFieldMapper.price("")).isNull();
        assertThat(TransactionFieldMapper.price(null)).isNull();
        assertThat(TransactionFieldMapper.price("abc")).isNull();
    }

    @Test
    void roundsExclusiveAreaHalfUpToTwoPlaces() {
        assertThat(TransactionFieldMapper.exclusiveArea("59.6851"))
                .isEqualByComparingTo(new BigDecimal("59.69"));
        assertThat(TransactionFieldMapper.exclusiveArea("59.4"))
                .isEqualByComparingTo(new BigDecimal("59.40"));
    }

    @Test
    void mapsValidDateAndRejectsInvalidDate() {
        assertThat(TransactionFieldMapper.dealDate("2026", "6", "4"))
                .isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(TransactionFieldMapper.dealDate("2026", "2", "30")).isNull();
    }

    @Test
    void mapsCancellationType() {
        assertThat(TransactionFieldMapper.canceled(" ")).isFalse();
        assertThat(TransactionFieldMapper.canceled("O")).isTrue();
    }

    @Test
    void dedupKeyUsesUnroundedAreaSource() {
        String key = TransactionFieldMapper.dedupKey(item("59.6851"));

        assertThat(key).contains("59.6851").doesNotContain("59.69|");
    }

    @Test
    void differentSourceAreasProduceDifferentDedupKeys() {
        assertThat(TransactionFieldMapper.dedupKey(item("59.6851")))
                .isNotEqualTo(TransactionFieldMapper.dedupKey(item("59.6912")));
    }

    private AptTradeItem item(String area) {
        return new AptTradeItem(
                "삼성동롯데아파트", "11680-168", "2026", "6", "4", "235,000",
                area, "2", "", "학동로", "00432", "00000", "11680", "10500"
        );
    }
}
