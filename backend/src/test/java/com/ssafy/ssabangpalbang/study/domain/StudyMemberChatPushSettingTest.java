package com.ssafy.ssabangpalbang.study.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StudyMemberChatPushSettingTest {

    @Test
    void 신규_멤버의_채팅_푸시는_기본_활성이다() {
        StudyMember member = StudyMember.createMember(1L, 2L);

        assertThat(member.isChatPushEnabled()).isTrue();
    }

    @Test
    void 재가입하면_채팅_푸시가_다시_활성화된다() {
        StudyMember member = StudyMember.createMember(1L, 2L);
        member.updateChatPushEnabled(false);
        member.leave(Instant.parse("2026-08-06T00:00:00Z"));

        member.reactivate();

        assertThat(member.isChatPushEnabled()).isTrue();
        assertThat(member.getStatus()).isEqualTo(StudyMemberStatus.ACTIVE);
    }
}
