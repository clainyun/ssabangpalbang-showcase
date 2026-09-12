package com.ssafy.ssabangpalbang.report.repository;

import java.time.Instant;

public interface PublicProfileReportRow {

    Long getReportId();

    String getResultJson();

    Boolean getFavoritedByMe();

    Long getApartmentId();

    String getApartmentName();

    Long getStudyId();

    String getStudyTitle();

    Long getParticipantCount();

    Instant getCompletedAt();
}
