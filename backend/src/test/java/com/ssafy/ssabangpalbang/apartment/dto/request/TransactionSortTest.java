package com.ssafy.ssabangpalbang.apartment.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionSortTest {

    @Test
    void createsSortForEveryAllowedValueWithIdTieBreaker() {
        assertSort("DEAL_DATE_DESC", "dealDate", Sort.Direction.DESC);
        assertSort("DEAL_DATE_ASC", "dealDate", Sort.Direction.ASC);
        assertSort("PRICE_DESC", "price", Sort.Direction.DESC);
        assertSort("PRICE_ASC", "price", Sort.Direction.ASC);
        assertSort("AREA_DESC", "exclusiveArea", Sort.Direction.DESC);
        assertSort("AREA_ASC", "exclusiveArea", Sort.Direction.ASC);
    }

    @Test
    void rejectsUnknownEmptyAndLowercaseValues() {
        assertInvalid("deal_date_desc");
        assertInvalid("UNKNOWN");
        assertInvalid("");
    }

    @Test
    void usesDealDateDescendingByDefault() {
        assertThat(TransactionSort.from(null)).isEqualTo(TransactionSort.DEAL_DATE_DESC);
    }

    private void assertSort(String value, String property, Sort.Direction direction) {
        Sort sort = TransactionSort.from(value).toSort();
        assertThat(sort.stream()).extracting(Sort.Order::getProperty)
                .containsExactly(property, "id");
        assertThat(sort.getOrderFor(property).getDirection()).isEqualTo(direction);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(direction);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> TransactionSort.from(value))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.APARTMENT_TRANSACTION_SORT_INVALID);
                    assertThat(exception.getData()).containsEntry("field", "sort");
                });
    }
}
