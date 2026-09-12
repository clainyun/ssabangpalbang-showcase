-- BE-028: 같은 스터디 멤버 익명 별점·선택 리뷰

CREATE TABLE member_review (
    id          BIGSERIAL PRIMARY KEY,
    study_id    BIGINT NOT NULL REFERENCES study(id),
    reviewer_id BIGINT NOT NULL REFERENCES member(id),
    reviewee_id BIGINT NOT NULL REFERENCES member(id),
    rating      INTEGER NOT NULL,
    content     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_review_rating
        CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_member_review_not_self
        CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT uq_member_review_study_reviewer_reviewee
        UNIQUE (study_id, reviewer_id, reviewee_id)
);

CREATE INDEX idx_member_review_reviewee_created
    ON member_review (reviewee_id, created_at DESC, id DESC);
