package com.ssafy.ssabangpalbang.fieldvisit.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * classpath JSON을 한 번 파싱해 불변 {@link ChecklistCatalog}로 보관한다.
 *
 * <p>로드 실패 시 예외를 삼키고 empty를 반환해 앱 기동·기존 생성 흐름을 보호한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChecklistCatalogLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ChecklistCatalogValidator validator;
    private final FieldVisitAiProperties properties;

    private final AtomicReference<Optional<ChecklistCatalog>> cache =
            new AtomicReference<>();

    public Optional<ChecklistCatalog> get() {
        Optional<ChecklistCatalog> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = cache.get();
            if (cached != null) {
                return cached;
            }
            Optional<ChecklistCatalog> loaded = loadQuietly();
            cache.set(loaded);
            return loaded;
        }
    }

    /** 테스트에서 캐시를 비울 때 사용한다. */
    void clearCache() {
        cache.set(null);
    }

    private Optional<ChecklistCatalog> loadQuietly() {
        try {
            return Optional.of(loadOrThrow());
        } catch (ChecklistCatalogException exception) {
            log.warn("Checklist catalog unavailable: {}", exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            log.warn(
                    "Checklist catalog load failed: {}",
                    exception.toString()
            );
            return Optional.empty();
        }
    }

    ChecklistCatalog loadOrThrow() {
        String location = properties.catalogClasspath();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new ChecklistCatalogException("catalog resource not found: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                ChecklistCatalogDocument document = objectMapper.readValue(
                        inputStream,
                        ChecklistCatalogDocument.class
                );
                validator.validate(document);
                return toCatalog(document);
            }
        } catch (ChecklistCatalogException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChecklistCatalogException(
                    "failed to parse checklist catalog JSON",
                    exception
            );
        }
    }

    private ChecklistCatalog toCatalog(ChecklistCatalogDocument document) {
        ChecklistCatalog.CatalogEnumsView enums = new ChecklistCatalog.CatalogEnumsView(
                document.enums().answerType(),
                document.enums().accessLevel(),
                document.enums().visitConditions(),
                document.enums().evidenceTypes(),
                document.enums().priorityCode()
        );
        return new ChecklistCatalog(
                document.version(),
                document.selectionPolicy(),
                document.items(),
                enums,
                document.categories().size()
        );
    }
}
