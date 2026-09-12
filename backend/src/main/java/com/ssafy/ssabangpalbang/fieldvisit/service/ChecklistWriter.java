package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 체크리스트와 항목을 별도 트랜잭션({@code REQUIRES_NEW})으로 원자 저장한다.
 *
 * <p>{@code (session_id, member_id)} UNIQUE 위반은 이 트랜잭션을 rollback-only로
 * 만들고 예외를 호출자에게 전파한다. 호출자는 실패한 트랜잭션과 무관한
 * 재조회로 기존 행을 반환해야 한다({@link com.ssafy.ssabangpalbang.chat.service.ChatMessageWriter}
 * 패턴).</p>
 */
@Component
@RequiredArgsConstructor
public class ChecklistWriter {

    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SavedChecklist save(
            Long sessionId,
            Long memberId,
            GeneratedChecklistContent content
    ) {
        Checklist checklist = checklistRepository.saveAndFlush(
                Checklist.create(sessionId, memberId, content.fallback())
        );

        List<ChecklistItem> items = new ArrayList<>();
        for (ChecklistAiGenerateResponse.Item item : content.response().items()) {
            items.add(ChecklistItem.create(
                    checklist.getId(),
                    item.category(),
                    item.title(),
                    item.subtitle(),
                    item.displayOrder(),
                    item.example()
            ));
        }
        List<ChecklistItem> savedItems = checklistItemRepository.saveAll(items);
        checklistItemRepository.flush();
        return new SavedChecklist(checklist, List.copyOf(savedItems));
    }

    public record SavedChecklist(
            Checklist checklist,
            List<ChecklistItem> items
    ) {
    }
}
