package com.ssafy.ssabangpalbang.chat.repository;

import com.ssafy.ssabangpalbang.chat.domain.ChatReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChatReadStatusRepository extends JpaRepository<ChatReadStatus, Long> {

    Optional<ChatReadStatus> findByStudyIdAndMemberId(Long studyId, Long memberId);

    /**
     * (study_id, member_id) 유일성 제약(V1 스키마)을 PostgreSQL의 원자적
     * {@code INSERT ... ON CONFLICT ... DO UPDATE}로 upsert한다.
     *
     * <p>동시에 같은 (studyId, memberId)로 요청이 들어와도 다음을 DB 레벨에서 보장한다.</p>
     * <ul>
     *     <li>행이 없으면 하나만 INSERT되고, UNIQUE 위반으로 인한 예외가 발생하지 않는다.</li>
     *     <li>행이 있으면 UPDATE만 수행되며, {@code GREATEST}로 더 과거 시각이
     *         이미 저장된 더 최신 시각을 덮어쓰지 않는다(진행 방향으로만 갱신).</li>
     * </ul>
     *
     * <p>이 메서드는 영속성 컨텍스트를 거치지 않는 네이티브 쓰기이므로,
     * 호출 직후 {@link #findByStudyIdAndMemberId}로 실제 저장된 값을 다시 조회해야 한다.
     * {@code clearAutomatically = true}로 영속성 컨텍스트를 비워 그 재조회가
     * 캐시된 값이 아닌 DB의 최종 값을 읽도록 한다.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    INSERT INTO chat_read_status (study_id, member_id, last_read_at)
                    VALUES (:studyId, :memberId, :lastReadAt)
                    ON CONFLICT (study_id, member_id)
                    DO UPDATE
                    SET last_read_at = GREATEST(
                        chat_read_status.last_read_at,
                        EXCLUDED.last_read_at
                    )
                    """,
            nativeQuery = true
    )
    void upsertLastReadAt(
            @Param("studyId") Long studyId,
            @Param("memberId") Long memberId,
            @Param("lastReadAt") Instant lastReadAt
    );
}
