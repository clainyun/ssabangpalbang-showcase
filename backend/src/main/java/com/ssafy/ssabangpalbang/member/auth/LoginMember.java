package com.ssafy.ssabangpalbang.member.auth;

/**
 * 인증된 요청의 회원 컨텍스트입니다.
 *
 * @param memberId 로그인한 회원 ID
 * @param active   회원이 서비스를 이용할 수 있는 상태인지 여부
 */
public record LoginMember(
        Long memberId,
        boolean active
) {
}
