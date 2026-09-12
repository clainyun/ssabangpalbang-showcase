package com.ssafy.ssabangpalbang.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StudyScheduleCreateRequest(@NotNull Instant startAt, Instant endAt,
                                         @NotBlank @Size(max = 200) String meetingPlace) { }
