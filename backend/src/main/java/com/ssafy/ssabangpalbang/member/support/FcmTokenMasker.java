package com.ssafy.ssabangpalbang.member.support;

public final class FcmTokenMasker {

    private static final int MASKED_LENGTH_THRESHOLD = 12;

    private FcmTokenMasker() {
    }

    public static String mask(String token) {
        if (token == null || token.length() <= MASKED_LENGTH_THRESHOLD) {
            return "***";
        }

        return token.substring(0, 6)
                + "***"
                + token.substring(token.length() - 4);
    }
}
