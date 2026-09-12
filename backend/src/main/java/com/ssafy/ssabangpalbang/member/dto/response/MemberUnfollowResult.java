package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;

public record MemberUnfollowResult(
        MemberResponseCode responseCode,
        MemberUnfollowResponse response
) {
}
