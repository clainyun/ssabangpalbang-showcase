package com.ssafy.ssabangpalbang.study.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sbpb.schedule-reminder")
public record ScheduleReminderProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 */10 * * * *") String cron,
        @DefaultValue("24") int leadTimeHours
) {
}
