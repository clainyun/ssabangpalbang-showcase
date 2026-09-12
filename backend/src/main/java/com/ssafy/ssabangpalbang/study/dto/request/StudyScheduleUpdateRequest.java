package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StudyScheduleUpdateRequest(Instant startAt, Instant endAt, @Size(max = 200) String meetingPlace) { }
