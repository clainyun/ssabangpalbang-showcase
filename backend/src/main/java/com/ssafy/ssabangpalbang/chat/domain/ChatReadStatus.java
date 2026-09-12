package com.ssafy.ssabangpalbang.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 회원·스터디별 채팅 읽음 상태다. (studyId, memberId) 조합은 하나만 유지한다.
 */
@Entity
@Table(name = "chat_read_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    public static ChatReadStatus create(Long studyId, Long memberId, Instant lastReadAt) {
        ChatReadStatus readStatus = new ChatReadStatus();
        readStatus.studyId = studyId;
        readStatus.memberId = memberId;
        readStatus.lastReadAt = lastReadAt;
        return readStatus;
    }

    /**
     * 읽음 시각을 갱신한다. 이미 더 최신 시각을 읽은 상태라면 과거로 되돌리지 않는다.
     */
    public void advanceLastReadAt(Instant lastReadAt) {
        if (this.lastReadAt == null || lastReadAt.isAfter(this.lastReadAt)) {
            this.lastReadAt = lastReadAt;
        }
    }
}
