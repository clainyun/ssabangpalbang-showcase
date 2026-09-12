package com.ssafy.ssabangpalbang.fieldvisit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitRouteProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** 선택된 시설 순서를 카카오 보행 경로 한 번으로 확정한다. */
@Component
public class KakaoWalkingRouteClient {

    private static final int MAX_WAYPOINTS = 5;

    private final RestClient restClient;
    private final FieldVisitRouteProperties properties;

    public KakaoWalkingRouteClient(
            @Qualifier("fieldVisitWalkingRestClient") RestClient restClient,
            FieldVisitRouteProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public KakaoWalkingRoute findRoute(
            WalkingRoutePoint origin,
            List<WalkingRoutePoint> orderedWaypoints
    ) {
        if (orderedWaypoints == null || orderedWaypoints.size() < 2
                || orderedWaypoints.size() > properties.getMaxWaypoints()
                || orderedWaypoints.size() > MAX_WAYPOINTS) {
            throw new KakaoWalkingRouteUnavailableException(
                    "카카오 보행 경로의 경유지 수가 유효하지 않습니다."
            );
        }
        validatePoint(origin);
        orderedWaypoints.forEach(KakaoWalkingRouteClient::validatePoint);
        String apiKey = properties.getPoi().getRestApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new KakaoWalkingRouteUnavailableException(
                    "카카오 보행 API 설정이 없습니다."
            );
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uri(origin, orderedWaypoints))
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response, orderedWaypoints.size());
        } catch (KakaoWalkingRouteUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KakaoWalkingRouteUnavailableException(
                    "카카오 보행 경로 조회에 실패했습니다.", exception
            );
        }
    }

    private URI uri(
            WalkingRoutePoint origin,
            List<WalkingRoutePoint> orderedWaypoints
    ) {
        WalkingRoutePoint destination = orderedWaypoints.get(orderedWaypoints.size() - 1);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUri(properties.getWalking().getBaseUrl())
                .path("/v2/routing/walk")
                .queryParam("start_x", origin.longitude())
                .queryParam("start_y", origin.latitude())
                .queryParam("end_x", destination.longitude())
                .queryParam("end_y", destination.latitude())
                .queryParam("input_coord", "WGS84")
                .queryParam("output_coord", "WGS84")
                .queryParam("route_mode", "SHORTEST");
        if (orderedWaypoints.size() > 1) {
            List<WalkingRoutePoint> vias = orderedWaypoints.subList(
                    0, orderedWaypoints.size() - 1
            );
            builder.queryParam("via_x", join(vias, false));
            builder.queryParam("via_y", join(vias, true));
        }
        return builder.build().encode().toUri();
    }

    private static String join(List<WalkingRoutePoint> points, boolean latitude) {
        return points.stream()
                .map(point -> Double.toString(
                        latitude ? point.latitude() : point.longitude()
                ))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }

    private static KakaoWalkingRoute parse(JsonNode response, int expectedLegCount) {
        if (response == null || !"OK".equals(response.path("status").asText())) {
            throw unavailable();
        }
        JsonNode route = response.path("route");
        JsonNode routeProperties = route.path("properties");
        int totalDistance = requiredNonNegativeInt(routeProperties, "totalDistance");
        int totalTime = requiredNonNegativeInt(routeProperties, "totalTime");
        JsonNode legsNode = route.path("legs");
        if (!legsNode.isArray() || legsNode.size() != expectedLegCount) {
            throw unavailable();
        }

        List<KakaoWalkingRoute.Leg> legs = new ArrayList<>();
        ArrayNode coordinates = JsonNodeFactory.instance.arrayNode();
        double previousLongitude = Double.NaN;
        double previousLatitude = Double.NaN;
        long legDistanceSum = 0;
        long legTimeSum = 0;
        for (JsonNode legNode : legsNode) {
            JsonNode legProperties = legNode.path("properties");
            int legDistance = requiredNonNegativeInt(legProperties, "distance");
            int legTime = requiredNonNegativeInt(legProperties, "time");
            legs.add(new KakaoWalkingRoute.Leg(
                    legDistance,
                    legTime
            ));
            legDistanceSum += legDistance;
            legTimeSum += legTime;
            JsonNode steps = legNode.path("steps");
            if (!steps.isArray() || steps.isEmpty()) {
                if (legDistance == 0 && legTime == 0) {
                    continue;
                }
                throw unavailable();
            }
            int beforeLeg = coordinates.size();
            for (JsonNode step : steps) {
                JsonNode points = step.path("path").path("points");
                if (!points.isArray() || points.isEmpty()) {
                    throw unavailable();
                }
                for (JsonNode point : points) {
                    if (!point.isArray() || point.size() < 2
                            || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                        throw unavailable();
                    }
                    double longitude = point.get(0).asDouble();
                    double latitude = point.get(1).asDouble();
                    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                            || latitude < -90 || latitude > 90
                            || longitude < -180 || longitude > 180) {
                        throw unavailable();
                    }
                    if (Double.compare(previousLongitude, longitude) == 0
                            && Double.compare(previousLatitude, latitude) == 0) {
                        continue;
                    }
                    ArrayNode coordinate = coordinates.addArray();
                    coordinate.add(longitude);
                    coordinate.add(latitude);
                    previousLongitude = longitude;
                    previousLatitude = latitude;
                }
            }
            if (coordinates.size() == beforeLeg && (legDistance > 0 || legTime > 0)) {
                throw unavailable();
            }
        }
        if (legDistanceSum != totalDistance || legTimeSum != totalTime) {
            throw unavailable();
        }
        if (coordinates.size() < 2) {
            throw unavailable();
        }
        ObjectNode geometry = JsonNodeFactory.instance.objectNode();
        geometry.put("type", "LineString");
        geometry.set("coordinates", coordinates);
        return new KakaoWalkingRoute(totalDistance, totalTime, legs, geometry);
    }

    private static int requiredNonNegativeInt(JsonNode parent, String name) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt() || value.asInt() < 0) {
            throw unavailable();
        }
        return value.asInt();
    }

    private static void validatePoint(WalkingRoutePoint point) {
        if (point == null
                || !Double.isFinite(point.latitude())
                || !Double.isFinite(point.longitude())
                || point.latitude() < -90 || point.latitude() > 90
                || point.longitude() < -180 || point.longitude() > 180) {
            throw new KakaoWalkingRouteUnavailableException(
                    "카카오 보행 경로 요청 좌표가 유효하지 않습니다."
            );
        }
    }

    private static KakaoWalkingRouteUnavailableException unavailable() {
        return new KakaoWalkingRouteUnavailableException(
                "카카오 보행 경로 응답을 사용할 수 없습니다."
        );
    }
}
