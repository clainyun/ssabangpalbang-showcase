package com.ssafy.ssabangpalbang.review.domain;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.Optional;

/**
 * 스터디 멤버 평가에서 사용하는 고정 태그다.
 *
 * <p>enum 상수 이름이 곧 DB에 저장되는 태그 코드(tag_code)이며, 한글 label과 emoji,
 * 분류(category)를 함께 가진다. 태그 카탈로그 테이블은 두지 않고 이 enum이 유일한 원천이다.
 * 프론트엔드는 동일한 코드 집합을 미러링한다.</p>
 */
public enum ReviewTag {

    PUNCTUAL("⏰", "시간 약속을 잘 지켜요", ReviewTagCategory.PERSON),
    GOOD_MANNERS("🙌", "매너가 좋아요", ReviewTagCategory.PERSON),
    EASY_COMMUNICATION("💬", "소통이 편해요", ReviewTagCategory.PERSON),
    CONSIDERATE("💚", "배려심이 깊어요", ReviewTagCategory.PERSON),
    WANT_AGAIN("🤝", "또 함께하고 싶어요", ReviewTagCategory.PERSON),
    LEADS_MOOD("😊", "분위기를 잘 이끌어요", ReviewTagCategory.PERSON),
    THOROUGH("🔍", "꼼꼼하게 살펴봐요", ReviewTagCategory.VISIT),
    LEADS_VISIT("🧭", "임장을 잘 리드해요", ReviewTagCategory.VISIT),
    WELL_PREPARED("📝", "준비를 잘해와요", ReviewTagCategory.VISIT),
    SHARP_ANALYSIS("🧠", "분석이 날카로워요", ReviewTagCategory.VISIT),
    GOOD_RECORDS("📸", "사진·기록을 잘 남겨요", ReviewTagCategory.VISIT),
    SHARES_INFO("📣", "정보 공유를 잘해요", ReviewTagCategory.VISIT),
    DILIGENT("✅", "성실하게 참여해요", ReviewTagCategory.VISIT),
    GOOD_QUESTIONS("❓", "질문이 좋아요", ReviewTagCategory.VISIT),
    ENERGETIC("⚡", "에너지가 넘쳐요", ReviewTagCategory.VISIT);

    private final String emoji;
    private final String label;
    private final ReviewTagCategory category;

    ReviewTag(String emoji, String label, ReviewTagCategory category) {
        this.emoji = emoji;
        this.label = label;
        this.category = category;
    }

    public String getCode() {
        return name();
    }

    public String getEmoji() {
        return emoji;
    }

    public String getLabel() {
        return label;
    }

    public ReviewTagCategory getCategory() {
        return category;
    }

    /**
     * 태그 코드로 태그를 찾는다. 알 수 없는 코드는 비어 있는 Optional을 돌려준다.
     * 조회·집계 경로처럼 잘못된 코드를 조용히 건너뛰어야 하는 곳에서 사용한다.
     */
    public static Optional<ReviewTag> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (ReviewTag tag : values()) {
            if (tag.name().equals(code)) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }

    public static boolean isValidCode(String code) {
        return findByCode(code).isPresent();
    }

    /**
     * 태그 코드를 태그로 변환한다. 유효하지 않으면 400 예외를 던진다.
     * 등록 요청 검증처럼 잘못된 코드를 거절해야 하는 곳에서 사용한다.
     */
    public static ReviewTag fromCode(String code) {
        return findByCode(code).orElseThrow(() ->
                new BusinessException(ErrorCode.MEMBER_REVIEW_TAG_INVALID));
    }
}
