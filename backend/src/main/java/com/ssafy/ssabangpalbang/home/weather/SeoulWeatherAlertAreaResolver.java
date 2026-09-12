package com.ssafy.ssabangpalbang.home.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class SeoulWeatherAlertAreaResolver {

    private static final SeoulWeatherAlertArea SOUTHEAST =
            new SeoulWeatherAlertArea("L1100100", "서울동남권");
    private static final SeoulWeatherAlertArea NORTHEAST =
            new SeoulWeatherAlertArea("L1100200", "서울동북권");
    private static final SeoulWeatherAlertArea SOUTHWEST =
            new SeoulWeatherAlertArea("L1100300", "서울서남권");
    private static final SeoulWeatherAlertArea NORTHWEST =
            new SeoulWeatherAlertArea("L1100400", "서울서북권");

    private static final Map<String, SeoulWeatherAlertArea> AREA_BY_DISTRICT =
            Map.ofEntries(
                    Map.entry("강동구", SOUTHEAST),
                    Map.entry("송파구", SOUTHEAST),
                    Map.entry("강남구", SOUTHEAST),
                    Map.entry("서초구", SOUTHEAST),
                    Map.entry("도봉구", NORTHEAST),
                    Map.entry("노원구", NORTHEAST),
                    Map.entry("강북구", NORTHEAST),
                    Map.entry("성북구", NORTHEAST),
                    Map.entry("동대문구", NORTHEAST),
                    Map.entry("중랑구", NORTHEAST),
                    Map.entry("성동구", NORTHEAST),
                    Map.entry("광진구", NORTHEAST),
                    Map.entry("강서구", SOUTHWEST),
                    Map.entry("양천구", SOUTHWEST),
                    Map.entry("구로구", SOUTHWEST),
                    Map.entry("영등포구", SOUTHWEST),
                    Map.entry("동작구", SOUTHWEST),
                    Map.entry("관악구", SOUTHWEST),
                    Map.entry("금천구", SOUTHWEST),
                    Map.entry("은평구", NORTHWEST),
                    Map.entry("종로구", NORTHWEST),
                    Map.entry("마포구", NORTHWEST),
                    Map.entry("서대문구", NORTHWEST),
                    Map.entry("중구", NORTHWEST),
                    Map.entry("용산구", NORTHWEST)
            );

    private final KakaoRegionClient kakaoRegionClient;

    Optional<SeoulWeatherAlertArea> resolve(HomeWeatherLocation location) {
        Optional<SeoulWeatherAlertArea> namedArea = fromText(location.name());
        if (namedArea.isPresent()) {
            return namedArea;
        }
        return kakaoRegionClient.resolveSeoulDistrict(
                        location.latitude(),
                        location.longitude()
                )
                .flatMap(SeoulWeatherAlertAreaResolver::fromText);
    }

    private static Optional<SeoulWeatherAlertArea> fromText(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return AREA_BY_DISTRICT.entrySet().stream()
                .filter(entry -> value.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
