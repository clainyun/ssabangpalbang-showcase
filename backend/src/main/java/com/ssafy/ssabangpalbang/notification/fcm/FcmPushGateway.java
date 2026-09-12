package com.ssafy.ssabangpalbang.notification.fcm;

import java.util.Map;

public interface FcmPushGateway {

    boolean isReady();

    void validateTestPush(String fcmToken);

    void sendTestPush(String fcmToken);

    void sendNotification(
            String fcmToken,
            String title,
            String body,
            Map<String, String> data
    );

    default void close() {
    }
}
