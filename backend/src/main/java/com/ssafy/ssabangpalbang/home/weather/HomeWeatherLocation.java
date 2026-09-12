package com.ssafy.ssabangpalbang.home.weather;

public record HomeWeatherLocation(
        double latitude,
        double longitude,
        String name,
        Basis basis
) {

    public enum Basis {
        CURRENT_LOCATION,
        NEXT_VISIT_APARTMENT,
        DEFAULT_LOCATION
    }
}
