package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;

import java.util.List;

/** 저장 전 단계에서 하나의 POI에 연결된 체크리스트 항목 묶음이다. */
public record RouteCandidate(
        FacilityType facilityType,
        KakaoLocalPoi poi,
        List<ChecklistItem> checklistItems,
        int distanceFromOriginMeters
) {

    public RouteCandidate {
        checklistItems = List.copyOf(checklistItems);
    }
}
