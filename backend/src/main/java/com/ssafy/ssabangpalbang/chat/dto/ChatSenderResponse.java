package com.ssafy.ssabangpalbang.chat.dto;

import com.ssafy.ssabangpalbang.member.domain.Member;

/**
 * 채팅 메시지 발신자 정보다. SYSTEM 메시지는 sender가 null이다.
 */
public record ChatSenderResponse(
        Long memberId,
        String nickname,
        String selectedCharacterId
) {
    public static ChatSenderResponse from(Member member) {
        return new ChatSenderResponse(
                member.getId(),
                member.getNickname(),
                member.getSelectedCharacterId()
        );
    }
}
