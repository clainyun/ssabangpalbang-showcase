package com.ssafy.ssabangpalbang.fieldvisit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class FieldVisitStartFingerprint {

    private FieldVisitStartFingerprint() {
    }

    static String of(Long studyId, double latitude, double longitude) {
        return hash(studyId + "|" + normalize(latitude) + "|" + normalize(longitude));
    }

    private static String normalize(double value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
