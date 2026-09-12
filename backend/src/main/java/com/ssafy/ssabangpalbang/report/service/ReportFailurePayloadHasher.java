package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.report.dto.request.ReportFailRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ReportFailurePayloadHasher {

    String hash(ReportFailRequest request) {
        String canonical = request.failedStage().name()
                + "\n" + request.errorCode().name()
                + "\n" + request.errorCode().safeMessage()
                + "\n" + request.retryable();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    boolean matches(String actualHash, String expectedHash) {
        if (actualHash == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
