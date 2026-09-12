package com.ssafy.ssabangpalbang.review.support;

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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;

@Component
public class MemberReviewCursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_ENCODED_CURSOR_LENGTH = 2048;

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    public MemberReviewCursorCodec(
            ObjectMapper objectMapper,
            JwtProperties jwtProperties
    ) {
        this.objectMapper = objectMapper;
        try {
            this.signingKey = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "평가 커서 서명 키를 초기화할 수 없습니다.",
                    exception
            );
        }
    }

    public String encode(MemberReviewCursor cursor) {
        CursorPayload payload = new CursorPayload(
                cursor.version(),
                cursor.revieweeId(),
                cursor.createdAt().toString(),
                cursor.reviewId()
        );
        try {
            String encodedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            objectMapper.writeValueAsBytes(payload)
                    );
            String signature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(sign(encodedPayload));
            return encodedPayload + "." + signature;
        } catch (JsonProcessingException exception) {
            throw invalidCursor();
        }
    }

    public MemberReviewCursor decode(
            String encoded,
            long expectedRevieweeId
    ) {
        if (encoded == null
                || encoded.isBlank()
                || encoded.length() > MAX_ENCODED_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        try {
            String[] parts = encoded.split("\\.", -1);
            if (parts.length != 2
                    || parts[0].isBlank()
                    || parts[1].isBlank()) {
                throw invalidCursor();
            }
            byte[] suppliedSignature = Base64.getUrlDecoder()
                    .decode(parts[1]);
            if (!MessageDigest.isEqual(
                    sign(parts[0]),
                    suppliedSignature
            )) {
                throw invalidCursor();
            }
            CursorPayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    CursorPayload.class
            );
            return validate(payload, expectedRevieweeId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException
                 | IOException
                 | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private MemberReviewCursor validate(
            CursorPayload payload,
            long expectedRevieweeId
    ) {
        if (payload == null
                || payload.v() == null
                || payload.v() != MemberReviewCursor.CURRENT_VERSION
                || payload.revieweeId() == null
                || payload.revieweeId() != expectedRevieweeId
                || payload.createdAt() == null
                || payload.reviewId() == null
                || payload.reviewId() < 1) {
            throw invalidCursor();
        }
        return new MemberReviewCursor(
                payload.v(),
                payload.revieweeId(),
                Instant.parse(payload.createdAt()),
                payload.reviewId()
        );
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(
                    encodedPayload.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "평가 커서 서명을 생성할 수 없습니다.",
                    exception
            );
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(
                ErrorCode.INVALID_CURSOR,
                Map.of(
                        "field", "cursor",
                        "reason", "커서가 유효하지 않거나 조회 회원과 일치하지 않습니다."
                )
        );
    }

    private record CursorPayload(
            Integer v,
            Long revieweeId,
            String createdAt,
            Long reviewId
    ) {
    }
}
