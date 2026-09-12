package com.ssafy.ssabangpalbang.report.repository;

import java.time.Instant;

public interface FavoriteReportRow {

    Long getReportId();

    String getResultJson();

    Long getApartmentId();

    String getApartmentName();

    String getApartmentAddress();

    String getDongName();

    Instant getCompletedAt();

    Instant getFavoritedAt();
}
