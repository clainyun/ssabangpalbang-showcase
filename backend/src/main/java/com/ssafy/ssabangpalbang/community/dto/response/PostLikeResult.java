package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.response.PostResponseCode;

public record PostLikeResult(
        PostResponseCode responseCode,
        PostLikeResponse response
) {
}
