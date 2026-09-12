package com.ssafy.ssabangpalbang.fieldvisit.stt.integration;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutboxStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchPort;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttCreatePersistenceCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttCreationTransaction;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttDispatchOutboxProcessor;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttLockedRequestValidator;
import com.ssafy.ssabangpalbang.fieldvisit.stt.service.SttReliabilityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class SttCreationTransactionIntegrationTest {

    @Autowired
    private SttCreationTransaction creationTransaction;

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private SttJobAttemptRepository sttJobAttemptRepository;

    @Autowired
    private SttDispatchOutboxRepository dispatchOutboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @MockitoBean
    private SttLockedRequestValidator lockedRequestValidator;

    @BeforeEach
    void stubLockedValidation() {
        org.mockito.Mockito.when(lockedRequestValidator.validate(any()))
                .thenReturn(new SttLockedRequestValidator.LockedRequestContext(
                        100L,
                        "stt/audio.webm",
                        "audio/webm"
                ));
    }

    @AfterEach
    void cleanUp() {
        dispatchOutboxRepository.deleteAll();
        sttJobAttemptRepository.deleteAll();
        sttJobRepository.deleteAll();
    }

    @Test
    void 커밋된_신규_작업만_AFTER_COMMIT에서_발행한다() {
        SttJob job = creationTransaction.create(command("stt-after-commit"));

        assertThat(sttJobRepository.findById(job.getId()))
                .get()
                .extracting(SttJob::getStatus)
                .isEqualTo(SttStatus.PENDING);
        assertThat(sttJobAttemptRepository.count()).isEqualTo(1);
        assertThat(dispatchOutboxRepository.count()).isEqualTo(1);
        assertThat(dispatchOutboxRepository.findAll().get(0).getStatus())
                .isEqualTo(SttDispatchOutboxStatus.PENDING);
    }

    @Test
    void 발행_실패에도_커밋된_작업은_PENDING으로_유지한다() {
        SttJob job = creationTransaction.create(command("stt-recovery"));

        assertThat(sttJobRepository.findById(job.getId()))
                .get()
                .extracting(SttJob::getStatus)
                .isEqualTo(SttStatus.PENDING);
        assertThat(dispatchOutboxRepository.count()).isEqualTo(1);
    }

    @Test
    void ack_after_database_rollback_is_published_again() {
        SttJob job = creationTransaction.create(command("stt-ack-rollback"));
        Long outboxId = dispatchOutboxRepository.findAll().get(0).getId();
        SttDispatchPort dispatchPort = mock();
        SttDispatchOutboxProcessor processor =
                new SttDispatchOutboxProcessor(
                        dispatchOutboxRepository,
                        sttJobRepository,
                        sttJobAttemptRepository,
                        dispatchPort,
                        reliabilityProperties(),
                        clock
                );
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            processor.process(outboxId);
            throw new IllegalStateException("commit boundary failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(dispatchOutboxRepository.findById(outboxId))
                .get()
                .satisfies(outbox -> {
                    assertThat(outbox.getStatus())
                            .isEqualTo(SttDispatchOutboxStatus.PENDING);
                    assertThat(outbox.getAttemptCount()).isZero();
                });
        assertThat(sttJobRepository.findById(job.getId()))
                .get()
                .extracting(SttJob::getLastDispatchedAt)
                .isNull();

        transaction.executeWithoutResult(status -> processor.process(outboxId));

        verify(dispatchPort, times(2)).dispatch(any());
        assertThat(dispatchOutboxRepository.findById(outboxId))
                .get()
                .extracting(outbox -> outbox.getStatus())
                .isEqualTo(SttDispatchOutboxStatus.PUBLISHED);
    }

    private SttReliabilityProperties reliabilityProperties() {
        return new SttReliabilityProperties(
                true,
                Duration.ofSeconds(1),
                10,
                3,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofSeconds(10),
                3,
                Duration.ofSeconds(30),
                Duration.ofHours(1)
        );
    }

    private SttCreatePersistenceCommand command(String sttId) {
        return new SttCreatePersistenceCommand(
                sttId,
                7L,
                10L,
                100L,
                90L,
                501L,
                UUID.randomUUID(),
                OffsetDateTime.of(
                        2026,
                        7,
                        25,
                        14,
                        25,
                        0,
                        0,
                        ZoneOffset.ofHours(9)
                ),
                "stt/audio.webm",
                "audio/webm"
        );
    }
}
