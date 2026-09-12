package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "field_visit_close_vote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitCloseVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_session_id", nullable = false)
    private Long sessionId;

    @Column(name = "field_participant_id", nullable = false)
    private Long participantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static FieldVisitCloseVote create(
            Long sessionId,
            Long participantId,
            Instant createdAt
    ) {
        FieldVisitCloseVote vote = new FieldVisitCloseVote();
        vote.sessionId = Objects.requireNonNull(sessionId);
        vote.participantId = Objects.requireNonNull(participantId);
        vote.createdAt = Objects.requireNonNull(createdAt);
        return vote;
    }
}
