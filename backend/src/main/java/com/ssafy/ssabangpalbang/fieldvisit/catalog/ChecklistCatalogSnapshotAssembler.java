package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 선택된 itemCode를 기존 generate 응답 형태(category/title/subtitle/displayOrder)로 조립한다.
 */
@Component
public class ChecklistCatalogSnapshotAssembler {

    public ChecklistAiGenerateResponse toGenerateResponse(
            List<String> itemCodes,
            ChecklistCatalog catalog
    ) {
        List<ChecklistAiGenerateResponse.Item> items = new ArrayList<>();
        int order = 1;
        for (String itemCode : itemCodes) {
            ChecklistCatalogItem catalogItem = catalog.findByItemCode(itemCode)
                    .orElseThrow(() -> new ChecklistCatalogException(
                            "missing catalog item for snapshot: " + itemCode
                    ));
            String category = catalogItem.categoryTitle();
            if (category.length() > ChecklistItem.CATEGORY_MAX_LENGTH) {
                throw new ChecklistCatalogException(
                        "categoryTitle exceeds max length for " + itemCode
                );
            }
            if (catalogItem.title() == null || catalogItem.title().isBlank()) {
                throw new ChecklistCatalogException("blank title for " + itemCode);
            }
            items.add(new ChecklistAiGenerateResponse.Item(
                    category,
                    catalogItem.title(),
                    catalogItem.subtitle(),
                    order++,
                    catalogItem.example()
            ));
        }
        return new ChecklistAiGenerateResponse(List.copyOf(items));
    }
}
