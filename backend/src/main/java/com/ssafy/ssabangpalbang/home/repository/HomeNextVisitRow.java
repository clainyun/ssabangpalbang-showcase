package com.ssafy.ssabangpalbang.home.repository;

import java.time.Instant;

public interface HomeNextVisitRow {

    Instant getStartAt();

    String getMeetingPlace();

    Long getStudyId();

    Integer getCapacity();

    Long getCurrentMemberCount();

    String getApartmentName();

    String getApartmentAddress();

    String getDistrictName();

    Double getLatitude();

    Double getLongitude();
}
