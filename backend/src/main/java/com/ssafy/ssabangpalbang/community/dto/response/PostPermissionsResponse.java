package com.ssafy.ssabangpalbang.community.dto.response;

public record PostPermissionsResponse(
        boolean canEdit,
        boolean canDelete,
        boolean canLike,
        boolean canComment
) {
}
