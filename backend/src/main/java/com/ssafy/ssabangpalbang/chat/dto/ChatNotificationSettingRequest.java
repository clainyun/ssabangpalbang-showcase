package com.ssafy.ssabangpalbang.chat.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public record ChatNotificationSettingRequest(
        @NotNull
        @JsonDeserialize(using = StrictBooleanDeserializer.class)
        Boolean pushEnabled
) {
}
