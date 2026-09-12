package com.ssafy.ssabangpalbang.notification.fcm;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class FirebaseAdminFcmPushGateway implements FcmPushGateway {

    private static final String CHANNEL_ID = "ssabangpalbang-alerts";
    private static final String NOTIFICATION_TYPE_KEY = "notificationType";
    private static final String TEST_PUSH_TYPE = "FCM_TEST_PUSH";
    private static final String TEST_PUSH_TITLE = "FCM 테스트 알림";
    private static final String TEST_PUSH_BODY = "서버에서 전송한 테스트 알림입니다.";

    private final FirebaseApp firebaseApp;
    private final boolean ownsFirebaseApp;
    private final AtomicBoolean closed = new AtomicBoolean();

    public FirebaseAdminFcmPushGateway(FirebaseApp firebaseApp) {
        this(firebaseApp, false);
    }

    public FirebaseAdminFcmPushGateway(
            FirebaseApp firebaseApp,
            boolean ownsFirebaseApp
    ) {
        this.firebaseApp = firebaseApp;
        this.ownsFirebaseApp = ownsFirebaseApp;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void validateTestPush(String fcmToken) {
        send(buildTestMessage(fcmToken), true);
    }

    @Override
    public void sendTestPush(String fcmToken) {
        send(buildTestMessage(fcmToken), false);
    }

    @Override
    public void sendNotification(
            String fcmToken,
            String title,
            String body,
            Map<String, String> data
    ) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(data)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(CHANNEL_ID)
                                .setSound("default")
                                .build())
                        .build())
                .build();
        send(message, false);
    }

    @Override
    public void close() {
        if (ownsFirebaseApp && closed.compareAndSet(false, true)) {
            try {
                firebaseApp.delete();
            } catch (RuntimeException exception) {
                closed.set(false);
                throw exception;
            }
        }
    }

    private Message buildTestMessage(String fcmToken) {
        return Message.builder()
                .setToken(fcmToken)
                .putData(NOTIFICATION_TYPE_KEY, TEST_PUSH_TYPE)
                .setNotification(Notification.builder()
                        .setTitle(TEST_PUSH_TITLE)
                        .setBody(TEST_PUSH_BODY)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(CHANNEL_ID)
                                .setSound("default")
                                .build())
                        .build())
                .build();
    }

    private void send(Message message, boolean dryRun) {
        try {
            FirebaseMessaging.getInstance(firebaseApp).send(message, dryRun);
        } catch (
                FirebaseMessagingException
                | IllegalArgumentException
                | IllegalStateException exception
        ) {
            throw new FcmPushDeliveryException(exception);
        }
    }
}
