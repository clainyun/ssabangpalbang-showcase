package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 카카오 로컬 API를 통해 시설 유형별 최근접 POI를 읽는다. */
@Component
public class KakaoLocalPoiClient {

    private final RestClient restClient;
    private final FieldVisitRouteProperties properties;
    private final Cache<RoutePoiCacheKey, List<KakaoLocalPoi>> cache;

    public KakaoLocalPoiClient(
            @Qualifier("fieldVisitRouteRestClient") RestClient restClient,
            FieldVisitRouteProperties properties,
            Cache<RoutePoiCacheKey, List<KakaoLocalPoi>> cache
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.cache = cache;
    }

    public Optional<KakaoLocalPoi> findNearest(
            FacilityType facilityType,
            Long apartmentId,
            double originLatitude,
            double originLongitude
    ) {
        int cacheRadius = FacilityType.maximumRadiusForCategory(
                facilityType.categoryGroupCode()
        );
        RoutePoiCacheKey cacheKey = new RoutePoiCacheKey(
                apartmentId, facilityType.categoryGroupCode(), cacheRadius
        );
        List<KakaoLocalPoi> pois;
        try {
            pois = cache.get(cacheKey, ignored -> fetchCategory(
                    facilityType.categoryGroupCode(),
                    cacheRadius,
                    originLatitude,
                    originLongitude
            ));
        } catch (KakaoLocalPoiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KakaoLocalPoiUnavailableException(
                    "카카오 주변 시설 조회에 실패했습니다.", exception
            );
        }
        return pois.stream()
                .filter(poi -> matchesType(facilityType, poi))
                .filter(poi -> GeoDistanceCalculator.distanceMeters(
                        originLatitude, originLongitude, poi.latitude(), poi.longitude()
                ) <= facilityType.radiusMeters())
                .min(Comparator.comparingInt(KakaoLocalPoi::distanceMeters)
                        .thenComparing(KakaoLocalPoi::id));
    }

    private List<KakaoLocalPoi> fetchCategory(
            String categoryGroupCode,
            int radiusMeters,
            double latitude,
            double longitude
    ) {
        String apiKey = properties.getPoi().getRestApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new KakaoLocalPoiUnavailableException(
                    "카카오 로컬 API 설정이 없습니다."
            );
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uri(categoryGroupCode, radiusMeters, latitude, longitude))
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("documents").isArray()) {
                throw new KakaoLocalPoiUnavailableException(
                        "카카오 주변 시설 응답을 사용할 수 없습니다."
                );
            }
            List<KakaoLocalPoi> pois = new ArrayList<>();
            for (JsonNode document : response.path("documents")) {
                parse(document).ifPresent(pois::add);
            }
            if (!response.path("documents").isEmpty() && pois.isEmpty()) {
                throw new KakaoLocalPoiUnavailableException(
                        "카카오 주변 시설 응답을 사용할 수 없습니다."
                );
            }
            return List.copyOf(pois);
        } catch (KakaoLocalPoiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KakaoLocalPoiUnavailableException(
                    "카카오 주변 시설 조회에 실패했습니다.", exception
            );
        }
    }

    private URI uri(
            String categoryGroupCode,
            int radiusMeters,
            double latitude,
            double longitude
    ) {
        return UriComponentsBuilder.fromUri(properties.getPoi().getBaseUrl())
                .path("/v2/local/search/category.json")
                .queryParam("category_group_code", categoryGroupCode)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", radiusMeters)
                .queryParam("sort", "distance")
                .queryParam("size", properties.getPoi().getPageSize())
                .build()
                .encode()
                .toUri();
    }

    private static Optional<KakaoLocalPoi> parse(JsonNode document) {
        String id = document.path("id").asText().trim();
        String name = document.path("place_name").asText().trim();
        if (id.isBlank() || name.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode distanceNode = document.get("distance");
            if (distanceNode == null || distanceNode.asText().isBlank()) {
                return Optional.empty();
            }
            double longitude = Double.parseDouble(document.path("x").asText());
            double latitude = Double.parseDouble(document.path("y").asText());
            int distance = Integer.parseInt(distanceNode.asText());
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || distance < 0 || latitude < -90 || latitude > 90
                    || longitude < -180 || longitude > 180) {
                return Optional.empty();
            }
            String roadAddress = document.path("road_address_name").asText().trim();
            String address = roadAddress.isBlank()
                    ? document.path("address_name").asText().trim()
                    : roadAddress;
            return Optional.of(new KakaoLocalPoi(
                    id,
                    name,
                    document.path("category_name").asText(),
                    address.isBlank() ? null : address,
                    latitude,
                    longitude,
                    distance
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean matchesType(FacilityType type, KakaoLocalPoi poi) {
        if (!type.requiresCategoryNameFilter()) {
            return true;
        }
        String lastCategory = lastCategoryToken(poi.categoryName());
        return switch (type) {
            case ELEMENTARY_SCHOOL -> "초등학교".equals(lastCategory)
                    || poi.name().endsWith("초등학교");
            case MIDDLE_SCHOOL -> "중학교".equals(lastCategory)
                    || poi.name().endsWith("중학교");
            case HIGH_SCHOOL -> "고등학교".equals(lastCategory)
                    || poi.name().endsWith("고등학교");
            case DAYCARE -> "어린이집".equals(lastCategory)
                    || poi.name().endsWith("어린이집");
            case HOSPITAL -> !isAnimalHospital(poi);
            default -> false;
        };
    }

    private static boolean isAnimalHospital(KakaoLocalPoi poi) {
        String category = poi.categoryName() == null ? "" : poi.categoryName();
        String name = poi.name() == null ? "" : poi.name();
        return category.contains("동물병원")
                || category.contains("반려동물")
                || name.contains("동물병원")
                || name.contains("동물의료센터")
                || name.contains("동물메디컬센터");
    }

    private static String lastCategoryToken(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return "";
        }
        int index = categoryName.lastIndexOf(" > ");
        return (index < 0 ? categoryName : categoryName.substring(index + 3)).trim();
    }
}
