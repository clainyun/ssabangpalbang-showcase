package com.ssafy.ssabangpalbang.fieldvisit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.fieldvisit.route")
@Validated
public class FieldVisitRouteProperties {

    @Valid
    private final Poi poi = new Poi();
    @Valid
    private final Walking walking = new Walking();
    @Min(2)
    private int minWaypoints = 2;
    @Min(1)
    @Max(5)
    private int maxWaypoints = 5;
    @PositiveOrZero
    private int waypointMergeMeters = 80;

    @AssertTrue(message = "최소 경유지 수는 최대 경유지 수보다 클 수 없습니다.")
    public boolean isWaypointRangeValid() {
        return minWaypoints <= maxWaypoints;
    }

    public Poi getPoi() {
        return poi;
    }

    public Walking getWalking() {
        return walking;
    }

    public int getMinWaypoints() {
        return minWaypoints;
    }

    public void setMinWaypoints(int minWaypoints) {
        this.minWaypoints = minWaypoints;
    }

    public int getMaxWaypoints() {
        return maxWaypoints;
    }

    public void setMaxWaypoints(int maxWaypoints) {
        this.maxWaypoints = maxWaypoints;
    }

    public int getWaypointMergeMeters() {
        return waypointMergeMeters;
    }

    public void setWaypointMergeMeters(int waypointMergeMeters) {
        this.waypointMergeMeters = waypointMergeMeters;
    }

    public static class Poi {

        @NotNull
        private URI baseUrl = URI.create("https://dapi.kakao.com");
        private String restApiKey = "";
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);
        @NotNull
        private Duration readTimeout = Duration.ofSeconds(3);
        @NotNull
        private Duration cacheTtl = Duration.ofDays(7);
        @Min(1)
        @Max(15)
        private int pageSize = 15;

        @AssertTrue(message = "POI timeout과 cache TTL은 0보다 커야 합니다.")
        public boolean isDurationRangeValid() {
            return isPositive(connectTimeout)
                    && isPositive(readTimeout)
                    && isPositive(cacheTtl);
        }

        private static boolean isPositive(Duration value) {
            return value != null && !value.isZero() && !value.isNegative();
        }

        public URI getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getRestApiKey() {
            return restApiKey;
        }

        public void setRestApiKey(String restApiKey) {
            this.restApiKey = restApiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
    }

    public static class Walking {

        @NotNull
        private URI baseUrl = URI.create("https://dapi.kakao.com");
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);
        @NotNull
        private Duration readTimeout = Duration.ofSeconds(5);

        @AssertTrue(message = "보행 API timeout은 0보다 커야 합니다.")
        public boolean isDurationRangeValid() {
            return isPositive(connectTimeout) && isPositive(readTimeout);
        }

        private static boolean isPositive(Duration value) {
            return value != null && !value.isZero() && !value.isNegative();
        }

        public URI getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
