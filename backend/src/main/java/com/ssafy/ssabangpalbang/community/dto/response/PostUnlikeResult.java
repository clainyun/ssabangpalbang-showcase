package com.ssafy.ssabangpalbang.community.dto.response;

import com.ssafy.ssabangpalbang.community.response.PostResponseCode;

public record PostUnlikeResult(
        PostResponseCode responseCode,
        PostUnlikeResponse response
) {
}
