package com.ssafy.ssabangpalbang.community.dto.response;

public record PostAuthorResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        String authorType
) {
}
