package com.ssafy.ssabangpalbang.notification.fcm;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.Map;

public class DisabledFcmPushGateway implements FcmPushGateway {

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void validateTestPush(String fcmToken) {
        throw new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED);
    }

    @Override
    public void sendTestPush(String fcmToken) {
        throw new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED);
    }

    @Override
    public void sendNotification(
            String fcmToken,
            String title,
            String body,
            Map<String, String> data
    ) {
        throw new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED);
    }
}
