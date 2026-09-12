package com.ssafy.ssabangpalbang.report.repository;

import java.time.Instant;

public interface MemberReportRow {

    Long getReportId();

    String getResultJson();

    String getStatus();

    Boolean getFavoritedByMe();

    Long getApartmentId();

    String getApartmentName();

    Long getStudyId();

    String getStudyTitle();

    Instant getVisitedAt();

    Long getParticipantCount();

    Boolean getCanViewEvidence();

    Instant getCompletedAt();
}
