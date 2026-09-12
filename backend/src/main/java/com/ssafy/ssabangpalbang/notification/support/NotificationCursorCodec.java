package com.ssafy.ssabangpalbang.notification.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.token.JwtProperties;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import io.jsonwebtoken.io.Decoders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

@Component
public class NotificationCursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_ENCODED_CURSOR_LENGTH = 2048;

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    public NotificationCursorCodec(
            ObjectMapper objectMapper,
            JwtProperties jwtProperties
    ) {
        this.objectMapper = objectMapper;
        try {
            this.signingKey = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "알림 커서 서명 키를 초기화할 수 없습니다.",
                    exception
            );
        }
    }

    public String encode(NotificationCursor cursor) {
        CursorPayload payload = new CursorPayload(
                cursor.version(),
                cursor.recipientId(),
                cursor.snapshotMaxId(),
                cursor.snapshotAt().toString(),
                cursor.unreadOnly(),
                cursor.unreadGroup(),
                cursor.sentAt().toString(),
                cursor.notificationId()
        );

        try {
            String encodedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(sign(encodedPayload));
            return encodedPayload + "." + signature;
        } catch (JsonProcessingException exception) {
            throw invalidCursor();
        }
    }

    public NotificationCursor decode(
            String encoded,
            long expectedRecipientId,
            boolean expectedUnreadOnly
    ) {
        if (encoded == null
                || encoded.isBlank()
                || encoded.length() > MAX_ENCODED_CURSOR_LENGTH) {
            throw invalidCursor();
        }

        try {
            String[] parts = encoded.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor();
            }
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), suppliedSignature)) {
                throw invalidCursor();
            }

            CursorPayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    CursorPayload.class
            );
            return validate(payload, expectedRecipientId, expectedUnreadOnly);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException
                 | IOException
                 | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private NotificationCursor validate(
            CursorPayload payload,
            long expectedRecipientId,
            boolean expectedUnreadOnly
    ) {
        if (payload == null
                || payload.v() == null
                || payload.v() != NotificationCursor.CURRENT_VERSION
                || payload.recipientId() == null
                || payload.recipientId() != expectedRecipientId
                || payload.snapshotMaxId() == null
                || payload.snapshotMaxId() < 1
                || payload.snapshotAt() == null
                || payload.unreadOnly() == null
                || payload.unreadOnly() != expectedUnreadOnly
                || payload.unreadGroup() == null
                || (payload.unreadOnly() && !payload.unreadGroup())
                || payload.sentAt() == null
                || payload.notificationId() == null
                || payload.notificationId() < 1
                || payload.notificationId() > payload.snapshotMaxId()) {
            throw invalidCursor();
        }

        return new NotificationCursor(
                payload.v(),
                payload.recipientId(),
                payload.snapshotMaxId(),
                OffsetDateTime.parse(payload.snapshotAt()),
                payload.unreadOnly(),
                payload.unreadGroup(),
                OffsetDateTime.parse(payload.sentAt()),
                payload.notificationId()
        );
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "알림 커서 서명을 생성할 수 없습니다.",
                    exception
            );
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(
                ErrorCode.NOTIFICATION_CURSOR_INVALID,
                java.util.Map.of(
                        "field", "cursor",
                        "reason", "커서가 유효하지 않거나 요청 조건과 일치하지 않습니다."
                )
        );
    }

    private record CursorPayload(
            Integer v,
            Long recipientId,
            Long snapshotMaxId,
            String snapshotAt,
            Boolean unreadOnly,
            Boolean unreadGroup,
            String sentAt,
            Long notificationId
    ) {
    }
}
