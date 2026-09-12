package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.dto.request.FcmTokenRegisterRequest;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenDeleteResponse;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenResponse;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRegistrationLock;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import com.ssafy.ssabangpalbang.member.support.FcmTokenMasker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmTokenService {

    private static final int MAX_VALUE_LENGTH = 255;
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(FcmTokenService.class);

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmTokenRegistrationLock fcmTokenRegistrationLock;
    private final LoginMemberResolver loginMemberResolver;

    @Transactional
    public RegistrationResult register(
            String deviceId,
            FcmTokenRegisterRequest request
    ) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        String normalizedToken = normalizeToken(request.fcmToken());
        LoginMember loginMember = loginMemberResolver.resolve();

        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        Long memberId = loginMember.memberId();
        log.info(
                "FCM token registration received: memberId={}, fcmToken={}",
                memberId,
                maskTokenForLog(normalizedToken)
        );
        fcmTokenRegistrationLock.acquire(
                normalizedDeviceId,
                normalizedToken
        );
        OffsetDateTime updatedAt = OffsetDateTime.now(SEOUL_ZONE_ID);

        fcmTokenRepository.deleteByDeviceIdAndOtherMember(
                normalizedDeviceId,
                memberId
        );
        fcmTokenRepository.deleteByTokenOnOtherOwner(
                normalizedToken,
                memberId,
                normalizedDeviceId
        );

        MemberResponseCode responseCode;
        String registrationResult;
        FcmToken fcmToken = fcmTokenRepository
                .findByMemberIdAndDeviceId(memberId, normalizedDeviceId)
                .orElse(null);

        if (fcmToken == null) {
            fcmTokenRepository.save(FcmToken.of(
                    memberId,
                    normalizedToken,
                    normalizedDeviceId,
                    updatedAt
            ));
            responseCode = MemberResponseCode.FCM_TOKEN_REGISTERED;
            registrationResult = "registered";
        } else {
            fcmToken.updateToken(normalizedToken, updatedAt);
            responseCode = MemberResponseCode.FCM_TOKEN_UPDATED;
            registrationResult = "updated";
        }

        logRegistrationCompletedAfterCommit(memberId, registrationResult, normalizedToken);

        return new RegistrationResult(
                responseCode,
                new FcmTokenResponse(normalizedDeviceId, true, updatedAt)
        );
    }

    @Transactional
    public DeletionResult delete(String deviceId) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        LoginMember loginMember = loginMemberResolver.resolve();

        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        fcmTokenRegistrationLock.acquireDevice(normalizedDeviceId);
        int deletedCount = fcmTokenRepository.deleteByMemberIdAndDeviceId(
                loginMember.memberId(),
                normalizedDeviceId
        );
        MemberResponseCode responseCode = deletedCount > 0
                ? MemberResponseCode.FCM_TOKEN_DELETED
                : MemberResponseCode.FCM_TOKEN_ALREADY_DELETED;

        return new DeletionResult(
                responseCode,
                new FcmTokenDeleteResponse(
                        normalizedDeviceId,
                        false,
                        OffsetDateTime.now(SEOUL_ZONE_ID)
                )
        );
    }

    private String normalizeDeviceId(String deviceId) {
        String normalizedDeviceId = deviceId == null ? "" : deviceId.strip();

        if (isInvalidValue(normalizedDeviceId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_DEVICE_ID_INVALID,
                    Map.of(
                            "field", "deviceId",
                            "reason", "기기 ID는 1자 이상 255자 이하이어야 합니다."
                    )
            );
        }

        return normalizedDeviceId;
    }

    private String normalizeToken(String token) {
        String normalizedToken = token == null ? "" : token.strip();

        if (isInvalidValue(normalizedToken)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_FCM_TOKEN_INVALID,
                    Map.of(
                            "field", "fcmToken",
                            "reason", "유효한 FCM 토큰을 입력해 주세요."
                    )
            );
        }

        return normalizedToken;
    }

    private boolean isInvalidValue(String value) {
        return value.isEmpty()
                || value.length() > MAX_VALUE_LENGTH
                || value.indexOf('\u0000') >= 0;
    }

    private void logRegistrationCompletedAfterCommit(
            Long memberId,
            String registrationResult,
            String token
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logRegistrationCompleted(memberId, registrationResult, token);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logRegistrationCompleted(memberId, registrationResult, token);
            }
        });
    }

    private static void logRegistrationCompleted(
            Long memberId,
            String registrationResult,
            String token
    ) {
        log.info(
                "FCM token registration committed: result={}, memberId={}, fcmToken={}",
                registrationResult,
                memberId,
                maskTokenForLog(token)
        );
    }

    private static String maskTokenForLog(String token) {
        return FcmTokenMasker.mask(token)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public record RegistrationResult(
            MemberResponseCode responseCode,
            FcmTokenResponse response
    ) {
    }

    public record DeletionResult(
            MemberResponseCode responseCode,
            FcmTokenDeleteResponse response
    ) {
    }
}
