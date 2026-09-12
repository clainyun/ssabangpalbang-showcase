-- BE-028: 멤버 평가를 별점 기반에서 태그·좋아요 기반으로 개편

-- 별점은 더 이상 필수 신호가 아니므로 NOT NULL 제약만 해제한다.
-- 기존 CHECK ck_member_review_rating (rating BETWEEN 1 AND 5)은 NULL을 허용하므로 그대로 둔다.
ALTER TABLE member_review ALTER COLUMN rating DROP NOT NULL;

-- 좋아요(하트) 신호. 기존 행은 좋아요를 남기지 않은 것으로 본다.
ALTER TABLE member_review ADD COLUMN liked BOOLEAN NOT NULL DEFAULT false;

-- 평가 태그. 태그 코드는 애플리케이션 enum(ReviewTag)으로 검증하며 별도 카탈로그 테이블은 두지 않는다.
CREATE TABLE member_review_tag (
    id        BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES member_review(id) ON DELETE CASCADE,
    tag_code  VARCHAR(40) NOT NULL,
    CONSTRAINT uq_member_review_tag UNIQUE (review_id, tag_code)
);

CREATE INDEX idx_member_review_tag_review
    ON member_review_tag (review_id);
