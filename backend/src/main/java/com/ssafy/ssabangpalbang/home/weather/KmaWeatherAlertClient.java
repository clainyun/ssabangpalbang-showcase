package com.ssafy.ssabangpalbang.home.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
class KmaWeatherAlertClient {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Set<Integer> ACTIVE_COMMANDS = Set.of(1, 3, 6, 7);

    private final RestClient restClient;
    private final HomeWeatherProperties properties;

    KmaWeatherAlertClient(
            @Qualifier("homeWeatherRestClient") RestClient restClient,
            HomeWeatherProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    KmaWeatherAlertSnapshot fetch(
            SeoulWeatherAlertArea area,
            Instant now
    ) {
        String serviceKey = properties.kmaWeatherAlertServiceKey();
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException(
                    "기상청 기상특보 API 설정이 없습니다."
            );
        }

        LocalDate today = now.atZone(SEOUL_ZONE_ID).toLocalDate();
        JsonNode root = restClient.get()
                .uri(uri(
                        area.code(),
                        today.minusDays(6),
                        today,
                        serviceKey
                ))
                .retrieve()
                .body(JsonNode.class);
        JsonNode response = requireNormalResponse(root);
        List<KmaWeatherAlertEvent> events = parseEvents(
                response.path("body").path("items").path("item")
        );

        Map<Integer, KmaWeatherAlertEvent> latestByType = new HashMap<>();
        for (KmaWeatherAlertEvent event : events) {
            latestByType.merge(
                    event.warningType(),
                    event,
                    this::latest
            );
        }

        List<KmaWeatherAlertData> activeAlerts = latestByType.values()
                .stream()
                .filter(event -> event.isActiveAt(now))
                .map(event -> toData(event, area))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingInt(KmaWeatherAlertData::levelPriority)
                        .reversed()
                        .thenComparing(
                                KmaWeatherAlertData::issuedAt,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(KmaWeatherAlertData::type))
                .toList();
        return new KmaWeatherAlertSnapshot(activeAlerts);
    }

    private URI uri(
            String areaCode,
            LocalDate from,
            LocalDate to,
            String serviceKey
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUri(properties.weatherAlertBaseUrl())
                .path("/getPwnCd")
                .queryParam("numOfRows", 100)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("fromTmFc", DATE.format(from))
                .queryParam("toTmFc", DATE.format(to))
                .queryParam("areaCode", areaCode);
        if (serviceKey.matches(".*%[0-9A-Fa-f]{2}.*")) {
            String query = builder.build().getQuery();
            String base = builder.replaceQuery(null).build().toUriString();
            return URI.create(base + "?serviceKey=" + serviceKey + "&" + query);
        }
        return builder.queryParam("serviceKey", serviceKey)
                .build()
                .encode()
                .toUri();
    }

    private JsonNode requireNormalResponse(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException(
                    "기상청 기상특보 응답이 없습니다."
            );
        }
        JsonNode response = root.path("response");
        String resultCode = response.path("header")
                .path("resultCode")
                .asText();
        if (!"0".equals(resultCode) && !"00".equals(resultCode)) {
            throw new IllegalStateException(
                    "기상청 기상특보 응답을 사용할 수 없습니다."
            );
        }
        return response;
    }

    private List<KmaWeatherAlertEvent> parseEvents(JsonNode itemNode) {
        List<KmaWeatherAlertEvent> events = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(node -> parseEvent(node).ifPresent(events::add));
        } else if (itemNode.isObject()) {
            parseEvent(itemNode).ifPresent(events::add);
        }
        return events;
    }

    private Optional<KmaWeatherAlertEvent> parseEvent(JsonNode node) {
        OffsetDateTime issuedAt = parseDateTime(node.path("tmFc").asText());
        if (issuedAt == null) {
            return Optional.empty();
        }
        return Optional.of(new KmaWeatherAlertEvent(
                node.path("warnVar").asInt(-1),
                node.path("warnStress").asInt(-1),
                node.path("command").asInt(-1),
                node.path("cancel").asInt(0),
                node.path("tmSeq").asInt(0),
                node.path("areaCode").asText(),
                node.path("areaName").asText(),
                issuedAt,
                parseDateTime(node.path("startTime").asText()),
                parseDateTime(node.path("endTime").asText())
        ));
    }

    private KmaWeatherAlertEvent latest(
            KmaWeatherAlertEvent first,
            KmaWeatherAlertEvent second
    ) {
        Comparator<KmaWeatherAlertEvent> comparator = Comparator
                .comparing(KmaWeatherAlertEvent::issuedAt)
                .thenComparingInt(KmaWeatherAlertEvent::sequence);
        return comparator.compare(first, second) >= 0 ? first : second;
    }

    private Optional<KmaWeatherAlertData> toData(
            KmaWeatherAlertEvent event,
            SeoulWeatherAlertArea fallbackArea
    ) {
        Optional<KmaWeatherAlertType> type = KmaWeatherAlertType.fromCode(
                event.warningType()
        );
        Optional<KmaWeatherAlertLevel> level = KmaWeatherAlertLevel.fromCode(
                event.warningLevel()
        );
        if (type.isEmpty() || level.isEmpty()) {
            return Optional.empty();
        }
        String areaCode = event.areaCode().isBlank()
                ? fallbackArea.code()
                : event.areaCode();
        String areaName = event.areaName().isBlank()
                ? fallbackArea.name()
                : event.areaName();
        return Optional.of(new KmaWeatherAlertData(
                type.get().name(),
                level.get().name(),
                type.get().label() + level.get().label(),
                areaCode,
                areaName,
                event.issuedAt(),
                event.effectiveAt() == null
                        ? event.issuedAt()
                        : event.effectiveAt(),
                level.get().priority()
        ));
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 12) {
            return null;
        }
        try {
            return LocalDateTime.parse(
                            digits.substring(0, 12),
                            DATE_TIME
                    )
                    .atZone(SEOUL_ZONE_ID)
                    .toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private record KmaWeatherAlertEvent(
            int warningType,
            int warningLevel,
            int command,
            int cancel,
            int sequence,
            String areaCode,
            String areaName,
            OffsetDateTime issuedAt,
            OffsetDateTime effectiveAt,
            OffsetDateTime endedAt
    ) {
        private boolean isActiveAt(Instant now) {
            OffsetDateTime effective = effectiveAt == null
                    ? issuedAt
                    : effectiveAt;
            return cancel == 0
                    && ACTIVE_COMMANDS.contains(command)
                    && !effective.toInstant().isAfter(now)
                    && (endedAt == null || endedAt.toInstant().isAfter(now));
        }
    }

    private enum KmaWeatherAlertType {
        STRONG_WIND(1, "강풍"),
        HEAVY_RAIN(2, "호우"),
        COLD_WAVE(3, "한파"),
        DRY(4, "건조"),
        STORM_SURGE(5, "폭풍해일"),
        HIGH_WAVES(6, "풍랑"),
        TYPHOON(7, "태풍"),
        HEAVY_SNOW(8, "대설"),
        YELLOW_DUST(9, "황사"),
        HEAT_WAVE(12, "폭염");

        private final int code;
        private final String label;

        KmaWeatherAlertType(int code, String label) {
            this.code = code;
            this.label = label;
        }

        private static Optional<KmaWeatherAlertType> fromCode(int code) {
            for (KmaWeatherAlertType value : values()) {
                if (value.code == code) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        private String label() {
            return label;
        }
    }

    private enum KmaWeatherAlertLevel {
        ADVISORY(0, "주의보", 1),
        WARNING(1, "경보", 2),
        SEVERE_WARNING(2, "중대경보", 3);

        private final int code;
        private final String label;
        private final int priority;

        KmaWeatherAlertLevel(int code, String label, int priority) {
            this.code = code;
            this.label = label;
            this.priority = priority;
        }

        private static Optional<KmaWeatherAlertLevel> fromCode(int code) {
            for (KmaWeatherAlertLevel value : values()) {
                if (value.code == code) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        private String label() {
            return label;
        }

        private int priority() {
            return priority;
        }
    }
}

record KmaWeatherAlertSnapshot(
        List<KmaWeatherAlertData> alerts
) {
    KmaWeatherAlertSnapshot {
        alerts = List.copyOf(alerts);
    }
}

record KmaWeatherAlertData(
        String type,
        String level,
        String title,
        String areaCode,
        String areaName,
        OffsetDateTime issuedAt,
        OffsetDateTime effectiveAt,
        int levelPriority
) {
}
