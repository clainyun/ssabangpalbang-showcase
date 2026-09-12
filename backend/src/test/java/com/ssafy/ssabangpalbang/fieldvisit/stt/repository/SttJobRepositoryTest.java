package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SttJobRepositoryTest {

    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.of(
            2026,
            7,
            25,
            14,
            25,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private SttJobAttemptRepository sttJobAttemptRepository;

    @Test
    void 작업과_최초_시도를_멱등_키로_조회한다() {
        UUID clientRequestId = UUID.randomUUID();
        SttJob job = sttJobRepository.saveAndFlush(job(
                "stt-one",
                90L,
                clientRequestId
        ));
        sttJobAttemptRepository.saveAndFlush(SttJobAttempt.initial(
                job.getId(),
                7L,
                clientRequestId,
                REQUESTED_AT
        ));

        assertThat(sttJobRepository.findByAudioFileId(90L))
                .contains(job);
        assertThat(sttJobRepository.findBySttId("stt-one"))
                .contains(job);
        assertThat(sttJobRepository.findByMemberIdAndInitialClientRequestId(
                7L,
                clientRequestId
        )).contains(job);
        assertThat(sttJobAttemptRepository
                .findByMemberIdAndClientRequestId(7L, clientRequestId))
                .get()
                .extracting(SttJobAttempt::getSttJobId)
                .isEqualTo(job.getId());
    }

    @Test
    void 같은_audioFileId의_작업을_DB에서_차단한다() {
        sttJobRepository.saveAndFlush(job(
                "stt-one",
                90L,
                UUID.randomUUID()
        ));

        assertThatThrownBy(() -> sttJobRepository.saveAndFlush(job(
                "stt-two",
                90L,
                UUID.randomUUID()
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_회원의_clientRequestId_시도를_DB에서_차단한다() {
        SttJob first = sttJobRepository.saveAndFlush(job(
                "stt-one",
                90L,
                UUID.randomUUID()
        ));
        SttJob second = sttJobRepository.saveAndFlush(job(
                "stt-two",
                91L,
                UUID.randomUUID()
        ));
        UUID duplicatedRequestId = UUID.randomUUID();
        sttJobAttemptRepository.saveAndFlush(SttJobAttempt.initial(
                first.getId(),
                7L,
                duplicatedRequestId,
                REQUESTED_AT
        ));

        assertThatThrownBy(() -> sttJobAttemptRepository.saveAndFlush(
                SttJobAttempt.initial(
                        second.getId(),
                        7L,
                        duplicatedRequestId,
                        REQUESTED_AT
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SttJob job(
            String sttId,
            Long audioFileId,
            UUID clientRequestId
    ) {
        return SttJob.create(
                sttId,
                7L,
                10L,
                100L,
                audioFileId,
                501L,
                clientRequestId,
                REQUESTED_AT
        );
    }
}
