package com.ssafy.ssabangpalbang.fieldvisit.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FieldVisitRoutePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void 잘못된_경유지_범위를_거부한다() {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.setMinWaypoints(1);
        properties.setMaxWaypoints(6);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("minWaypoints", "maxWaypoints");
    }

    @Test
    void 잘못된_POI_페이지와_시간_설정을_거부한다() {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.getPoi().setPageSize(16);
        properties.getPoi().setReadTimeout(Duration.ZERO);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("poi.pageSize", "poi.durationRangeValid");
    }

    @Test
    void 잘못된_보행_API_시간_설정을_거부한다() {
        FieldVisitRouteProperties properties = new FieldVisitRouteProperties();
        properties.getWalking().setReadTimeout(Duration.ZERO);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("walking.durationRangeValid");
    }
}
