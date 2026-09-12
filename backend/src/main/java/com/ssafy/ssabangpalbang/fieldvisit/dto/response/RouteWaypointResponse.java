package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import java.util.List;

public record RouteWaypointResponse(
        Long waypointId,
        int sequence,
        String facilityType,
        String name,
        String address,
        double latitude,
        double longitude,
        String kakaoPlaceId,
        int distanceFromOriginM,
        int distanceFromPrevM,
        int walkMinutesFromPrev,
        int stayMinutes,
        String guide,
        List<RouteChecklistItemResponse> myItems,
        int myItemCount,
        int sharedItemCount
) {

    public record RouteChecklistItemResponse(
            Long checklistItemId,
            String category,
            String title,
            String subtitle,
            boolean isCompleted,
            int recordCount
    ) {
    }
}
