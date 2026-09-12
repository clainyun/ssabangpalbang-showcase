package com.ssafy.ssabangpalbang.fieldvisit.stt.repository;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SttJobAttemptRepository
        extends JpaRepository<SttJobAttempt, Long> {

    Optional<SttJobAttempt> findByMemberIdAndClientRequestId(
            Long memberId,
            UUID clientRequestId
    );

    Optional<SttJobAttempt> findTopBySttJobIdOrderByAttemptNoDesc(
            Long sttJobId
    );
}
