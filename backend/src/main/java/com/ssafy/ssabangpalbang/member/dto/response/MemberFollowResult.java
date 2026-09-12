package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;

public record MemberFollowResult(
        MemberResponseCode responseCode,
        MemberFollowResponse response
) {
}
