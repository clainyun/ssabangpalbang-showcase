package com.ssafy.ssabangpalbang.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.report.internal")
public record ReportInternalProperties(
        @DefaultValue("") String token,
        @DefaultValue("30m") Duration leaseDuration
) {

    private static final Duration MINIMUM_LEASE_DURATION =
            Duration.ofMinutes(30);

    public ReportInternalProperties {
        token = token == null ? "" : token;
        if (leaseDuration == null
                || leaseDuration.compareTo(MINIMUM_LEASE_DURATION) < 0) {
            throw new IllegalArgumentException(
                    "report internal leaseDuration must be at least 30 minutes"
            );
        }
    }

    @Override
    public String toString() {
        return "ReportInternalProperties[token=[REDACTED], leaseDuration="
                + leaseDuration + "]";
    }
}
