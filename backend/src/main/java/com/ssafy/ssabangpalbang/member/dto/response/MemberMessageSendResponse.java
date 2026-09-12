package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.notification.entity.Notification;

import java.time.OffsetDateTime;

public record MemberMessageSendResponse(
        Long recipientId,
        String recipientNickname,
        Long notificationId,
        String clientMessageId,
        OffsetDateTime sentAt
) {

    public static MemberMessageSendResponse of(
            Notification notification,
            Member recipient,
            String clientMessageId
    ) {
        return new MemberMessageSendResponse(
                recipient.getId(),
                recipient.getNickname(),
                notification.getId(),
                clientMessageId,
                notification.getSentAt()
        );
    }
}
