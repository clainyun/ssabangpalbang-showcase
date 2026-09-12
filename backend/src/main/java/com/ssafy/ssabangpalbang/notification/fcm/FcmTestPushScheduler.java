package com.ssafy.ssabangpalbang.notification.fcm;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class FcmTestPushScheduler {

    private static final Duration DELIVERY_DELAY = Duration.ofSeconds(5);
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(
            FcmTestPushScheduler.class
    );

    private final TaskScheduler taskScheduler;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmPushGateway fcmPushGateway;
    private final Clock clock;
    private final ConcurrentMap<DeliveryKey, PendingDelivery> pendingDeliveries =
            new ConcurrentHashMap<>();

    @Autowired
    public FcmTestPushScheduler(
            @Qualifier("fcmTestPushTaskScheduler") TaskScheduler taskScheduler,
            FcmTokenRepository fcmTokenRepository,
            FcmPushGateway fcmPushGateway
    ) {
        this(
                taskScheduler,
                fcmTokenRepository,
                fcmPushGateway,
                Clock.systemUTC()
        );
    }

    public FcmTestPushScheduler(
            TaskScheduler taskScheduler,
            FcmTokenRepository fcmTokenRepository,
            FcmPushGateway fcmPushGateway,
            Clock clock
    ) {
        this.taskScheduler = taskScheduler;
        this.fcmTokenRepository = fcmTokenRepository;
        this.fcmPushGateway = fcmPushGateway;
        this.clock = clock;
    }

    public OffsetDateTime schedule(Long memberId, String deviceId) {
        DeliveryKey deliveryKey = new DeliveryKey(memberId, deviceId);

        try {
            PendingDelivery pendingDelivery = pendingDeliveries.compute(
                    deliveryKey,
                    (key, existingDelivery) -> scheduleIfNecessary(
                            key,
                            existingDelivery,
                            memberId,
                            deviceId
                    )
            );
            return pendingDelivery.scheduledAt();
        } catch (RuntimeException exception) {
            log.warn(
                    "FCM test push scheduling failed: memberId={}, reason=scheduler_unavailable",
                    memberId
            );
            throw new BusinessException(ErrorCode.FCM_PUSH_SCHEDULING_FAILED);
        }
    }

    private PendingDelivery scheduleIfNecessary(
            DeliveryKey deliveryKey,
            PendingDelivery existingDelivery,
            Long memberId,
            String deviceId
    ) {
        if (existingDelivery != null
                && existingDelivery.state() == DeliveryState.SCHEDULED) {
            return existingDelivery;
        }

        Instant deliveryAt = clock.instant().plus(DELIVERY_DELAY);
        OffsetDateTime scheduledAt = deliveryAt
                .atZone(SEOUL_ZONE_ID)
                .toOffsetDateTime();
        PendingDelivery pendingDelivery = new PendingDelivery(
                scheduledAt,
                DeliveryState.SCHEDULED
        );
        ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(
                () -> executeScheduledDelivery(
                        deliveryKey,
                        pendingDelivery,
                        memberId,
                        deviceId
                ),
                deliveryAt
        );
        if (scheduledFuture == null) {
            throw new IllegalStateException("FCM test push was not scheduled.");
        }
        return pendingDelivery;
    }

    private void executeScheduledDelivery(
            DeliveryKey deliveryKey,
            PendingDelivery pendingDelivery,
            Long memberId,
            String deviceId
    ) {
        if (!pendingDeliveries.remove(deliveryKey, pendingDelivery)) {
            return;
        }

        try {
            deliverLatestToken(memberId, deviceId);
        } catch (FcmPushDeliveryException exception) {
            log.warn(
                    "FCM test push delivery failed: memberId={}, reason=provider_failure",
                    memberId
            );
        } catch (DataAccessException exception) {
            log.warn(
                    "FCM test push delivery failed: memberId={}, reason=database_failure",
                    memberId
            );
        } catch (RuntimeException exception) {
            log.error(
                    "FCM test push delivery failed: memberId={}, reason=unexpected_failure, errorType={}",
                    memberId,
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private void deliverLatestToken(Long memberId, String deviceId) {
        FcmToken latestToken = fcmTokenRepository
                .findByMemberIdAndDeviceId(memberId, deviceId)
                .orElse(null);

        if (latestToken == null) {
            log.info(
                    "FCM test push skipped: memberId={}, reason=token_missing",
                    memberId
            );
            return;
        }

        fcmPushGateway.sendTestPush(latestToken.getToken());
        log.info(
                "FCM test push handed off: memberId={}, status=provider_accepted",
                memberId
        );
    }

    private record DeliveryKey(Long memberId, String deviceId) {
    }

    private record PendingDelivery(
            OffsetDateTime scheduledAt,
            DeliveryState state
    ) {
    }

    private enum DeliveryState {
        SCHEDULED
    }
}
