package com.ssafy.ssabangpalbang.fieldvisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 체크리스트 항목이다(AI-002).
 *
 * <p>{@code category}는 AI/fallback이 만든 동적 문자열이다(최대 30자).
 * {@code (checklist_id, display_order)} UNIQUE와 title 공백 CHECK가 DB에
 * 있으므로, 저장 전에도 동일 불변식을 검증한다. {@code displayOrder}는
 * 1 이상이어야 하며, 연속 번호 여부는 강제하지 않는다.</p>
 */
@Entity
@Table(name = "checklist_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    public static final int CATEGORY_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checklist_id", nullable = false)
    private Long checklistId;

    @Column(nullable = false, length = CATEGORY_MAX_LENGTH)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String subtitle;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    // 항목별 예시 메모(선택). AI/카탈로그가 채우며, 없으면 프론트가 기본 안내 문구를 쓴다.
    @Column(columnDefinition = "TEXT")
    private String example;

    public static ChecklistItem create(
            Long checklistId,
            String category,
            String title,
            String subtitle,
            int displayOrder,
            String example
    ) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "체크리스트 항목의 category는 비어 있을 수 없습니다."
            );
        }
        if (category.length() > CATEGORY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "체크리스트 항목의 category는 "
                            + CATEGORY_MAX_LENGTH
                            + "자를 넘을 수 없습니다."
            );
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "체크리스트 항목의 title은 비어 있을 수 없습니다."
            );
        }
        if (displayOrder < 1) {
            throw new IllegalArgumentException(
                    "체크리스트 항목의 displayOrder는 1 이상이어야 합니다."
            );
        }
        ChecklistItem item = new ChecklistItem();
        item.checklistId = Objects.requireNonNull(checklistId);
        item.category = category;
        item.title = title;
        item.subtitle = subtitle;
        item.displayOrder = displayOrder;
        item.example = example;
        return item;
    }
}
