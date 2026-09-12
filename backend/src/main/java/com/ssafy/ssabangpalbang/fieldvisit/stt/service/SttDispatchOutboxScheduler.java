package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutboxStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${ssabangpalbang.fieldvisit.stt.reliability.enabled:false} "
                + "and ${ssabangpalbang.fieldvisit.stt.kafka.enabled:false}"
)
public class SttDispatchOutboxScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(SttDispatchOutboxScheduler.class);

    private final SttDispatchOutboxRepository outboxRepository;
    private final SttDispatchOutboxProcessor processor;
    private final SttReliabilityProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${ssabangpalbang.fieldvisit.stt.reliability.outbox-poll-interval:1s}"
    )
    public void publishReadyRequests() {
        outboxRepository.findReadyIds(
                        SttDispatchOutboxStatus.PENDING,
                        OffsetDateTime.now(clock),
                        PageRequest.of(0, properties.batchSize())
                )
                .forEach(this::processSafely);
    }

    private void processSafely(Long outboxId) {
        try {
            processor.process(outboxId);
        } catch (RuntimeException failure) {
            log.error(
                    "Unexpected STT outbox processing failure. outboxId={}",
                    outboxId,
                    failure
            );
        }
    }
}
