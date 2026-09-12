package com.ssafy.ssabangpalbang.fieldvisit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssabangpalbang.fieldvisit.start")
public class FieldVisitStartProperties {

    /**
     * GPS 임장 시작 허용 반경(미터). 환경변수 {@code FIELD_VISIT_START_ALLOWED_RADIUS_METERS}.
     */
    private int allowedRadiusMeters = 1000;

    public int getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }

    public void setAllowedRadiusMeters(int allowedRadiusMeters) {
        this.allowedRadiusMeters = allowedRadiusMeters;
    }

    public int allowedRadiusMeters() {
        return allowedRadiusMeters;
    }
}
