package com.ssafy.ssabangpalbang.home.service;

import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherLocation;

import java.time.LocalDate;

record HomeCoreData(
        LocalDate today,
        long unreadNotificationCount,
        HomeResponse.NextVisit nextVisit,
        HomeWeatherLocation nextVisitWeatherLocation
) {
}
