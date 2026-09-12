package com.ssafy.ssabangpalbang.apartment.dataload.dto;

public record AptTradeItem(
        String aptName,
        String aptSeq,
        String dealYear,
        String dealMonth,
        String dealDay,
        String dealAmount,
        String exclusiveArea,
        String floor,
        String cancellationType,
        String roadName,
        String roadMainNumber,
        String roadSubNumber,
        String sigunguCode,
        String legalDongCode
) {
}
