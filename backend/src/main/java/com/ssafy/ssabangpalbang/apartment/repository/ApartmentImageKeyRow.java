package com.ssafy.ssabangpalbang.apartment.repository;

/**
 * apartment_image 배치 조회용 프로젝션. 찜 목록처럼 여러 아파트의 대표 이미지를
 * 한 번에 가져올 때 사용해 N+1 조회를 피한다.
 */
public interface ApartmentImageKeyRow {

    Long getApartmentId();

    String getObjectKey();
}
