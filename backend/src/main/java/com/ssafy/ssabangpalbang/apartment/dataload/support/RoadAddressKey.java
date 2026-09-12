package com.ssafy.ssabangpalbang.apartment.dataload.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RoadAddressKey {

    private static final Pattern PROVINCE = Pattern.compile("^\\S+(특별시|광역시|특별자치시|도)\\s+");
    private static final Pattern DISTRICT = Pattern.compile("^\\S+(시|군|구)\\s+");
    private static final Pattern ROAD_ADDRESS = Pattern.compile("^(.+?)\\s+(\\d+)(?:-(\\d+))?");

    private RoadAddressKey() {
    }

    public static String fromFullAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String normalized = address.trim().replaceAll("\\s+", " ");
        normalized = PROVINCE.matcher(normalized).replaceFirst("");
        String previous;
        do {
            previous = normalized;
            normalized = DISTRICT.matcher(normalized).replaceFirst("");
        } while (!previous.equals(normalized));

        Matcher matcher = ROAD_ADDRESS.matcher(normalized);
        if (!matcher.find()) {
            return "";
        }
        String roadName = matcher.group(1).replaceAll("\\s+", "");
        if (!roadName.endsWith("로") && !roadName.endsWith("길")) {
            return "";
        }
        try {
            int mainNumber = Integer.parseInt(matcher.group(2));
            int subNumber = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return roadName + "|" + mainNumber + "|" + subNumber;
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    public static String fromParts(String roadName, String mainNumber, String subNumber) {
        if (roadName == null || roadName.isBlank() || mainNumber == null || mainNumber.isBlank()) {
            return "";
        }
        try {
            String normalizedRoadName = roadName.replaceAll("\\s+", "");
            int normalizedMainNumber = Integer.parseInt(mainNumber.trim());
            int normalizedSubNumber = subNumber == null || subNumber.isBlank()
                    ? 0 : Integer.parseInt(subNumber.trim());
            return normalizedRoadName + "|" + normalizedMainNumber + "|" + normalizedSubNumber;
        } catch (NumberFormatException exception) {
            return "";
        }
    }
}
