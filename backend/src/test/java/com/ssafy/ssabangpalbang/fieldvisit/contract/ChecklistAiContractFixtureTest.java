package com.ssafy.ssabangpalbang.fieldvisit.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiGenerateResponse;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectRequest;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiSelectResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java ↔ FastAPI 공유 JSON fixture 계약 테스트다.
 *
 * <p>Jenkins는 {@code backend} Docker context만 COPY하므로 저장소 루트
 * {@code contracts/}에 의존하지 않는다. fixture는 classpath
 * {@code contracts/ai-002/}에 두고, 루트 canonical 복사본과의 동등성은
 * Python 테스트({@code test_shared_contract_fixtures})에서 SHA-256으로 검증한다.</p>
 */
class ChecklistAiContractFixtureTest {

    private static final String REQUEST_RESOURCE =
            "contracts/ai-002/checklist_generate_request.json";
    private static final String RESPONSE_RESOURCE =
            "contracts/ai-002/checklist_generate_response.json";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void request_fixture는_Java_DTO로_역직렬화된다() throws Exception {
        ChecklistAiGenerateRequest request = objectMapper.readValue(
                readClasspath(REQUEST_RESOURCE),
                ChecklistAiGenerateRequest.class
        );

        assertThat(request.personalization().member().memberPurpose()).isEqualTo("RESIDENCE");
        assertThat(request.personalization().member().priorities())
                .containsExactly("TRANSPORT", "SAFETY", "NOISE");
        assertThat(request.personalization().study().studyPurpose()).isEqualTo("RESIDENCE");
        assertThat(request.personalization().apartment().apartmentId()).isEqualTo(15L);
    }

    @Test
    void response_fixture는_Java_DTO로_역직렬화된다() throws Exception {
        ChecklistAiGenerateResponse response = objectMapper.readValue(
                readClasspath(RESPONSE_RESOURCE),
                ChecklistAiGenerateResponse.class
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).displayOrder()).isEqualTo(1);
        assertThat(response.items().get(1).subtitle()).isNull();
    }

    @Test
    void request_fixture_필드명이_camelCase_contracts다() throws Exception {
        JsonNode root = objectMapper.readTree(readClasspath(REQUEST_RESOURCE));
        JsonNode member = root.path("personalization").path("member");

        assertThat(member.has("memberPurpose")).isTrue();
        assertThat(member.has("purpose")).isFalse();
        assertThat(root.path("personalization").path("study").has("studyPurpose")).isTrue();
    }

    @Test
    void select_request_fixture는_Java_DTO로_역직렬화된다() throws Exception {
        ChecklistAiSelectRequest request = objectMapper.readValue(
                readClasspath("contracts/ai-002/checklist_select_request.json"),
                ChecklistAiSelectRequest.class
        );

        assertThat(request.selectionVersion()).isEqualTo("v3-select-1");
        assertThat(request.targetItemCount()).isEqualTo(25);
        assertThat(request.mappedPurpose()).isEqualTo("LIVE");
        assertThat(request.selectedPriorities()).containsExactly("TRANSPORTATION", "SAFETY");
        assertThat(request.shortlist()).hasSizeGreaterThanOrEqualTo(25);
        assertThat(request.shortlist().get(0).itemCode()).isNotBlank();
        assertThat(request.shortlist().get(0).isCommonCore()).isTrue();
    }

    @Test
    void select_response_fixture는_shortlist_부분집합이고_개수가_일치한다() throws Exception {
        ChecklistAiSelectRequest request = objectMapper.readValue(
                readClasspath("contracts/ai-002/checklist_select_request.json"),
                ChecklistAiSelectRequest.class
        );
        ChecklistAiSelectResponse response = objectMapper.readValue(
                readClasspath("contracts/ai-002/checklist_select_response.json"),
                ChecklistAiSelectResponse.class
        );

        Set<String> shortlistCodes = request.shortlist().stream()
                .map(ChecklistAiSelectRequest.ShortlistItem::itemCode)
                .collect(Collectors.toSet());

        assertThat(response.itemCodes()).hasSize(request.targetItemCount());
        assertThat(response.itemCodes()).hasSize(new HashSet<>(response.itemCodes()).size());
        assertThat(shortlistCodes).containsAll(response.itemCodes());
    }

    private static String readClasspath(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        assertThat(resource.exists())
                .as("classpath resource must exist: %s", location)
                .isTrue();
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
