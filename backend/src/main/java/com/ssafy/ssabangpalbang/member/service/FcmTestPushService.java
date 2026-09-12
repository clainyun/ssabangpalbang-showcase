package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTestPushResponse;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushDeliveryException;
import com.ssafy.ssabangpalbang.notification.fcm.FcmPushGateway;
import com.ssafy.ssabangpalbang.notification.fcm.FcmTestPushScheduler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FcmTestPushService {

    private static final int MAX_VALUE_LENGTH = 255;
    private static final Logger log = LoggerFactory.getLogger(
            FcmTestPushService.class
    );

    private final FcmTokenRepository fcmTokenRepository;
    private final LoginMemberResolver loginMemberResolver;
    private final FcmPushGateway fcmPushGateway;
    private final FcmTestPushScheduler fcmTestPushScheduler;

    public FcmTestPushResponse sendToCurrentDevice(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LoginMember loginMember = loginMemberResolver.resolve();

        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        FcmToken fcmToken = fcmTokenRepository
                .findByMemberIdAndDeviceId(loginMember.memberId(), normalizedDeviceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_FCM_TOKEN_NOT_FOUND
                ));

        if (!fcmPushGateway.isReady()) {
            throw new BusinessException(ErrorCode.FCM_PUSH_NOT_CONFIGURED);
        }

        try {
            fcmPushGateway.validateTestPush(fcmToken.getToken());
        } catch (FcmPushDeliveryException exception) {
            log.warn(
                    "FCM test push validation failed: memberId={}, reason=provider_rejected",
                    loginMember.memberId()
            );
            throw new BusinessException(ErrorCode.FCM_PUSH_DELIVERY_FAILED);
        }

        return new FcmTestPushResponse(
                normalizedDeviceId,
                fcmTestPushScheduler.schedule(
                        loginMember.memberId(),
                        normalizedDeviceId
                )
        );
    }

    private String normalizeDeviceId(String deviceId) {
        String normalizedDeviceId = deviceId == null ? "" : deviceId.strip();

        if (normalizedDeviceId.isEmpty()
                || normalizedDeviceId.length() > MAX_VALUE_LENGTH
                || normalizedDeviceId.indexOf('\u0000') >= 0) {
            throw new BusinessException(
                    ErrorCode.MEMBER_DEVICE_ID_INVALID,
                    Map.of(
                            "field", "deviceId",
                            "reason", "기기 ID는 1자 이상 255자 이하여야 합니다."
                    )
            );
        }

        return normalizedDeviceId;
    }
}
