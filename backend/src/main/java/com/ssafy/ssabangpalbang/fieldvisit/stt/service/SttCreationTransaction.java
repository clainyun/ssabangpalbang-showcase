package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttDispatchOutbox;
import com.ssafy.ssabangpalbang.fieldvisit.stt.integration.SttDispatchCommand;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttDispatchOutboxRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SttCreationTransaction {

    private static final String DEFAULT_LANGUAGE = "ko-KR";

    private final SttJobRepository sttJobRepository;
    private final SttJobAttemptRepository sttJobAttemptRepository;
    private final SttDispatchOutboxRepository dispatchOutboxRepository;
    private final SttLockedRequestValidator lockedRequestValidator;

    @Transactional
    public SttJob create(SttCreatePersistenceCommand command) {
        SttLockedRequestValidator.LockedRequestContext lockedContext =
                lockedRequestValidator.validate(command);
        SttJob job = sttJobRepository.saveAndFlush(SttJob.create(
                command.sttId(),
                command.memberId(),
                command.studyId(),
                lockedContext.sessionId(),
                command.audioFileId(),
                command.checklistItemId(),
                command.clientRequestId(),
                command.requestedAt()
        ));

        sttJobAttemptRepository.saveAndFlush(SttJobAttempt.initial(
                job.getId(),
                command.memberId(),
                command.clientRequestId(),
                command.requestedAt()
        ));

        dispatchOutboxRepository.save(SttDispatchOutbox.pending(
                job.getId(),
                new SttDispatchCommand(
                        job.getSttId(),
                        1,
                        job.getAudioFileId(),
                        lockedContext.objectKey(),
                        lockedContext.contentType(),
                        DEFAULT_LANGUAGE
                ),
                command.requestedAt()
        ));
        return job;
    }
}
