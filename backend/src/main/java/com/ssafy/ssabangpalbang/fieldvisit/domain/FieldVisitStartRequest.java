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
@Table(name = "field_visit_start_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldVisitStartRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "client_request_id", nullable = false, length = 100)
    private String clientRequestId;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FieldVisitStartRequest create(
            Long memberId,
            Long studyId,
            Long sessionId,
            Long participantId,
            String clientRequestId,
            String requestFingerprint,
            Instant createdAt
    ) {
        FieldVisitStartRequest request = new FieldVisitStartRequest();
        request.memberId = Objects.requireNonNull(memberId);
        request.studyId = Objects.requireNonNull(studyId);
        request.sessionId = Objects.requireNonNull(sessionId);
        request.participantId = Objects.requireNonNull(participantId);
        request.clientRequestId = Objects.requireNonNull(clientRequestId);
        request.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        request.createdAt = Objects.requireNonNull(createdAt);
        return request;
    }
}
