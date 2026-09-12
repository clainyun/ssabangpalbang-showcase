package com.ssafy.ssabangpalbang.fieldvisit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FieldRecordFingerprint {

    private FieldRecordFingerprint() {
    }

    static String ofText(Long checklistItemId, String textContent) {
        return hash("TEXT|" + checklistItemId + "|" + textContent.trim());
    }

    static String ofPhoto(Long checklistItemId, Long photoFileId) {
        return hash("PHOTO|" + checklistItemId + "|" + photoFileId);
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
