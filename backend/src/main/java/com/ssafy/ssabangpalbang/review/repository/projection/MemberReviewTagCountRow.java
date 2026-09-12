package com.ssafy.ssabangpalbang.review.repository.projection;

/**
 * 피평가자가 받은 태그를 tag_code별로 집계한 행이다.
 * label/emoji/category는 서비스에서 ReviewTag enum으로 보강한다.
 */
public interface MemberReviewTagCountRow {

    String getTagCode();

    long getTagCount();
}
