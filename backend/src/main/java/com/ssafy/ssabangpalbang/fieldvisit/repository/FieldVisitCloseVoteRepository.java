package com.ssafy.ssabangpalbang.fieldvisit.repository;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCloseVote;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface FieldVisitCloseVoteRepository
        extends Repository<FieldVisitCloseVote, Long> {

    long countBySessionId(Long sessionId);

    boolean existsBySessionIdAndParticipantId(Long sessionId, Long participantId);

    Optional<FieldVisitCloseVote> findBySessionIdAndParticipantId(
            Long sessionId,
            Long participantId
    );

    FieldVisitCloseVote save(FieldVisitCloseVote vote);

    FieldVisitCloseVote saveAndFlush(FieldVisitCloseVote vote);
}
