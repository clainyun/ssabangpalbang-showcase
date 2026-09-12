package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;

public record MemberMessageSendResult(
        MemberResponseCode responseCode,
        MemberMessageSendResponse response,
        boolean created
) {
}
