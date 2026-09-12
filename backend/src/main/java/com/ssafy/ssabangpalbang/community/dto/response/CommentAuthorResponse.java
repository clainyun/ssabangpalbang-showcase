package com.ssafy.ssabangpalbang.community.dto.response;

public record CommentAuthorResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId
) {
}
