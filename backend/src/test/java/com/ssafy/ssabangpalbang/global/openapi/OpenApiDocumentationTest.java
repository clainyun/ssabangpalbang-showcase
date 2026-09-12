package com.ssafy.ssabangpalbang.global.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void FcmTokenOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/members/me/devices/{deviceId}/fcm-token")
                .contains("bearerAuth")
                .doesNotContain("X-Debug-Member-Id")
                .doesNotContain("X-Debug-Member-Active")
                .contains("FcmTokenRegisterRequest")
                .contains("FcmTokenResponse")
                .contains("FcmTokenDeleteResponse");
    }

    @Test
    void FcmTestPushScheduleOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode operation = objectMapper.readTree(openApiDocument)
                .at("/paths/~1api~1v1~1members~1me~1devices~1{deviceId}~1test-fcm-push/post");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/202").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/502").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/503").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("FcmTestPushResponse");

        JsonNode responseProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/FcmTestPushResponse/properties");
        assertThat(responseProperties.has("deviceId")).isTrue();
        assertThat(responseProperties.has("scheduledAt")).isTrue();
        assertThat(responseProperties.has("sentAt")).isFalse();
        assertThat(responseProperties.has("fcmToken")).isFalse();
        assertThat(responseProperties.has("messageId")).isFalse();
    }

    @Test
    void OnboardingOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/members/me/onboarding")
                .contains("bearerAuth")
                .contains("OnboardingRequest")
                .contains("OnboardingResponse");

        JsonNode requestProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/OnboardingRequest/properties");
        assertThat(requestProperties.size()).isEqualTo(8);
        assertThat(requestProperties.has("purpose")).isTrue();
        assertThat(requestProperties.has("maritalStatus")).isTrue();
        assertThat(requestProperties.has("hasVehicle")).isTrue();
        assertThat(requestProperties.has("hasChildren")).isTrue();
        assertThat(requestProperties.has("priorities")).isTrue();
        assertThat(requestProperties.has("ageGroup")).isTrue();
        assertThat(requestProperties.has("ageGroupPublicAgreed")).isTrue();
        assertThat(requestProperties.has("selectedCharacterId")).isTrue();
        assertThat(requestProperties.has("skipped")).isFalse();
        assertThat(requestProperties.has("householdType")).isFalse();
        assertThat(requestProperties.has("budget")).isFalse();
        assertThat(requestProperties.has("interestRegion")).isFalse();
        assertThat(requestProperties.has("interestRegionPublicAgreed")).isFalse();
    }

    @Test
    void MemberProfileUpdateOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/members/me")
                .contains("내 프로필 수정")
                .contains("bearerAuth")
                .contains("MemberProfileUpdateRequest")
                .contains("MemberProfileUpdateResponse");

        JsonNode requestProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/MemberProfileUpdateRequest/properties");
        assertThat(requestProperties.size()).isEqualTo(9);
        assertThat(requestProperties.has("nickname")).isTrue();
        assertThat(requestProperties.has("purpose")).isTrue();
        assertThat(requestProperties.has("priorities")).isTrue();
        assertThat(requestProperties.has("hasVehicle")).isTrue();
        assertThat(requestProperties.has("householdType")).isFalse();
        assertThat(requestProperties.has("budget")).isFalse();
        assertThat(requestProperties.has("interestRegion")).isFalse();
        assertThat(requestProperties.has("interestRegionPublicAgreed")).isFalse();
        assertThat(requestProperties.has("nicknameProvided")).isFalse();
        assertThat(requestProperties.has("anyField")).isFalse();
    }

    @Test
    void MemberPublicProfileOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/members/{memberId}")
                .contains("다른 사용자 공개 프로필 조회")
                .contains("bearerAuth")
                .contains("MemberPublicProfileResponse")
                .contains("participatingStudyCount")
                .contains("canSendMessage")
                .contains("PublicProfileStudyResponse")
                .contains("PublicProfileReportResponse")
                .contains("PublicProfileFollowingResponse");

        JsonNode profileProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/MemberPublicProfileResponse/properties");
        assertThat(profileProperties.has("reviewSummary")).isTrue();
        assertThat(profileProperties.has("section")).isTrue();
        assertThat(profileProperties.has("studies")).isTrue();
        assertThat(profileProperties.has("reports")).isTrue();
        assertThat(profileProperties.has("followings")).isTrue();

        JsonNode reportProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/PublicProfileReportResponse/properties");
        assertThat(reportProperties.has("reportId")).isTrue();
        assertThat(reportProperties.has("publicId")).isFalse();
        assertThat(reportProperties.has("visibility")).isFalse();
        assertThat(reportProperties.has("canViewEvidence")).isFalse();
    }

    @Test
    void MemberReviewListOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at("/paths/~1api~1v1~1members~1{memberId}~1reviews/get");
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();

        JsonNode parameters = operation.path("parameters");
        assertThat(parameters.findValuesAsText("name"))
                .containsExactlyInAnyOrder("memberId", "cursor", "size");

        JsonNode listProperties = document.at(
                "/components/schemas/MemberReviewListResponse/properties");
        assertThat(listProperties.has("summary")).isTrue();
        assertThat(listProperties.has("content")).isTrue();
        assertThat(listProperties.has("nextCursor")).isTrue();
        assertThat(listProperties.has("hasNext")).isTrue();

        JsonNode itemProperties = document.at(
                "/components/schemas/MemberReviewItemResponse/properties");
        assertThat(itemProperties.has("reviewId")).isTrue();
        assertThat(itemProperties.has("tags")).isTrue();
        assertThat(itemProperties.has("liked")).isTrue();
        assertThat(itemProperties.has("content")).isTrue();
        assertThat(itemProperties.has("createdAt")).isTrue();
        assertThat(itemProperties.has("rating")).isFalse();
        assertThat(itemProperties.has("reviewerId")).isFalse();
        assertThat(itemProperties.has("studyId")).isFalse();

        JsonNode summaryProperties = document.at(
                "/components/schemas/MemberReviewSummaryResponse/properties");
        assertThat(summaryProperties.has("topTags")).isTrue();
        assertThat(summaryProperties.has("likeReceivedCount")).isTrue();
        assertThat(summaryProperties.has("reviewCount")).isTrue();
        assertThat(summaryProperties.has("averageRating")).isFalse();

        JsonNode tagViewProperties = document.at(
                "/components/schemas/ReviewTagView/properties");
        assertThat(tagViewProperties.has("code")).isTrue();
        assertThat(tagViewProperties.has("label")).isTrue();
        assertThat(tagViewProperties.has("emoji")).isTrue();

        JsonNode tagCountProperties = document.at(
                "/components/schemas/ReviewTagCountView/properties");
        assertThat(tagCountProperties.has("code")).isTrue();
        assertThat(tagCountProperties.has("category")).isTrue();
        assertThat(tagCountProperties.has("count")).isTrue();

        JsonNode myProfileProperties = document.at(
                "/components/schemas/MemberProfileResponse/properties");
        assertThat(myProfileProperties.has("reviewSummary")).isTrue();
    }

    @Test
    void MemberReportListOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/members/me/reports")
                .contains("내 리포트 목록 조회")
                .contains("bearerAuth")
                .contains("MemberReportResponse")
                .contains("ApartmentSummary")
                .contains("StudySummary")
                .contains("canViewEvidence");
    }

    @Test
    void HomeOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at("/paths/~1api~1v1~1home/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("홈 화면 통합 조회");
        assertThat(operation.toString()).contains("bearerAuth");
        assertThat(operation.at("/responses/200").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/400").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/401").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/404").isMissingNode())
                .isFalse();
        assertThat(openApiDocument)
                .contains("HomeResponse")
                .contains("unreadNotificationCount")
                .contains("apartmentName")
                .contains("locationBasis")
                .contains("weatherAlerts")
                .contains("effectiveAt")
                .contains("nextVisit");
        assertThat(openApiDocument).doesNotContain("ultraFineDustValue");
        assertThat(openApiDocument).doesNotContain("\"warning\"");
    }

    @Test
    void OperationalEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void SocialLoginOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1auth~1social-login/post"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("네이버/카카오 간편 로그인");
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/503").isMissingNode()).isFalse();
        assertThat(operation.path("security").isMissingNode()).isTrue();

        JsonNode requestProperties = document.at(
                "/components/schemas/SocialLoginRequest/properties"
        );
        assertThat(requestProperties.has("provider")).isTrue();
        assertThat(requestProperties.has("authorizationCode")).isTrue();
        assertThat(requestProperties.has("redirectUri")).isTrue();
        assertThat(requestProperties.has("state")).isTrue();
        assertThat(openApiDocument)
                .contains("ExistingMember")
                .contains("SignupRequired")
                .contains("socialSignupToken")
                .contains("expiresIn");
    }

    @Test
    void ApartmentOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/apartments/{apartmentId}")
                .contains("/api/v1/apartments/{apartmentId}/transactions")
                .contains("아파트")
                .contains("bearerAuth")
                .contains("ApartmentDetailResponse")
                .contains("ApartmentTransactionResponse");
    }

    @Test
    void MemberUnfollowOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode operation = objectMapper.readTree(openApiDocument).at(
                "/paths/~1api~1v1~1members~1{memberId}~1follow/delete"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");

        JsonNode responseProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/MemberUnfollowResponse/properties");
        assertThat(responseProperties.has("memberId")).isTrue();
        assertThat(responseProperties.has("nickname")).isTrue();
        assertThat(responseProperties.has("selectedCharacterId")).isTrue();
        assertThat(responseProperties.has("isFollowing")).isTrue();
        assertThat(responseProperties.has("canSendMessage")).isTrue();
        assertThat(responseProperties.has("followingCount")).isTrue();
        assertThat(responseProperties.has("unfollowedAt")).isTrue();
    }

    @Test
    void MemberFollowingListOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1members~1me~1followings/get"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");

        JsonNode listProperties = document.at(
                "/components/schemas/MemberFollowingListResponse/properties"
        );
        assertThat(listProperties.has("content")).isTrue();
        assertThat(listProperties.has("totalCount")).isTrue();
        assertThat(listProperties.has("nextCursor")).isTrue();
        assertThat(listProperties.has("hasNext")).isTrue();

        JsonNode itemProperties = document.at(
                "/components/schemas/MemberFollowingResponse/properties"
        );
        assertThat(itemProperties.has("memberId")).isTrue();
        assertThat(itemProperties.has("nickname")).isTrue();
        assertThat(itemProperties.has("profileImageUrl")).isTrue();
        assertThat(itemProperties.has("selectedCharacterId")).isTrue();
        assertThat(itemProperties.has("ageGroup")).isTrue();
        assertThat(itemProperties.has("interestRegion")).isFalse();
        assertThat(itemProperties.has("participatingStudyCount")).isTrue();
        assertThat(itemProperties.has("isFollowing")).isTrue();
        assertThat(itemProperties.has("canSendMessage")).isTrue();
        assertThat(itemProperties.has("followedAt")).isTrue();
    }

    @Test
    void StudyOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/studies")
                .contains("/api/v1/studies/{studyId}")
                .contains("스터디")
                .contains("bearerAuth")
                .contains("StudyCreateRequest")
                .contains("StudyCreateResponse")
                .contains("StudyDetailResponse");
    }

    @Test
    void StudyCancelOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}/delete");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/409").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/500").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains(
                "StudyCancelResponse",
                "bearerAuth",
                "AUTH_ACCESS_TOKEN_INVALID",
                "STUDY_CANCEL_FORBIDDEN",
                "MEMBER_NOT_FOUND",
                "STUDY_NOT_FOUND",
                "STUDY_ALREADY_CANCELED",
                "STUDY_CANCEL_NOT_ALLOWED",
                "COMMON_INTERNAL_SERVER_ERROR"
        );

        JsonNode responseProperties = document.at(
                "/components/schemas/StudyCancelResponse/properties");
        assertThat(responseProperties.has("studyId")).isTrue();
        assertThat(responseProperties.has("status")).isTrue();
        assertThat(responseProperties.has("canceledAt")).isTrue();
        assertThat(responseProperties.size()).isEqualTo(3);
    }

    @Test
    void StudyApplicationOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/studies/{studyId}/applications")
                .contains("\"post\"")
                .contains("StudyApplicationCreateRequest")
                .contains("StudyApplicationCreateResponse")
                .contains("bearerAuth")
                .contains("\"201\"")
                .contains("\"400\"")
                .contains("\"401\"")
                .contains("\"404\"")
                .contains("\"409\"");

        JsonNode requestProperties = objectMapper.readTree(openApiDocument)
                .at("/components/schemas/StudyApplicationCreateRequest/properties");
        assertThat(requestProperties.at("/intro/maxLength").asInt()).isEqualTo(200);
        assertThat(requestProperties.at("/purpose/enum").toString())
                .isEqualTo("[\"RESIDENCE\",\"INVESTMENT\",\"STUDY\"]");
    }

    @Test
    void StudyRecruitmentCloseAndMemberKickOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode closeOperation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1recruitment~1close/patch"
        );
        JsonNode kickOperation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1members~1{memberId}/delete"
        );

        assertThat(closeOperation.isMissingNode()).isFalse();
        assertThat(closeOperation.at("/summary").asText())
                .isEqualTo("스터디 모집 조기 마감");
        assertThat(closeOperation.toString()).contains("bearerAuth");
        assertResponses(closeOperation, "200", "401", "403", "404", "409", "500");

        assertThat(kickOperation.isMissingNode()).isFalse();
        assertThat(kickOperation.at("/summary").asText())
                .isEqualTo("스터디 멤버 강퇴");
        assertThat(kickOperation.toString()).contains("bearerAuth");
        assertResponses(kickOperation, "200", "400", "401", "403", "404", "409", "500");

        JsonNode closeProperties = document.at(
                "/components/schemas/StudyRecruitmentCloseResponse/properties"
        );
        assertThat(closeProperties.size()).isEqualTo(6);
        assertThat(closeProperties.has("studyId")).isTrue();
        assertThat(closeProperties.has("status")).isTrue();
        assertThat(closeProperties.has("currentMemberCount")).isTrue();
        assertThat(closeProperties.has("capacity")).isTrue();
        assertThat(closeProperties.has("pendingApplicationCount")).isTrue();
        assertThat(closeProperties.has("recruitmentClosedAt")).isTrue();

        JsonNode kickProperties = document.at(
                "/components/schemas/StudyMemberKickResponse/properties"
        );
        assertThat(kickProperties.size()).isEqualTo(6);
        assertThat(kickProperties.has("studyId")).isTrue();
        assertThat(kickProperties.has("memberId")).isTrue();
        assertThat(kickProperties.has("currentMemberCount")).isTrue();
        assertThat(kickProperties.has("capacity")).isTrue();
        assertThat(kickProperties.has("studyStatus")).isTrue();
        assertThat(kickProperties.has("kickedAt")).isTrue();
    }

    @Test
    void StudyRecruitmentReopenOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode reopenOperation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1recruitment~1open/patch"
        );

        assertThat(reopenOperation.isMissingNode()).isFalse();
        assertThat(reopenOperation.at("/summary").asText())
                .isEqualTo("스터디 모집 재개");
        assertThat(reopenOperation.toString()).contains("bearerAuth");
        assertResponses(reopenOperation, "200", "401", "403", "404", "409", "500");
    }

    private void assertResponses(JsonNode operation, String... statuses) {
        for (String responseStatus : statuses) {
            assertThat(operation.at("/responses/" + responseStatus).isMissingNode())
                    .as("response status %s", responseStatus)
                    .isFalse();
        }
    }

    private void assertJsonEnvelope(
            JsonNode document,
            JsonNode operation,
            String responseStatus,
            String expectedDataSchema
    ) {
        JsonNode responseSchema = operation.at(
                "/responses/" + responseStatus
                        + "/content/application~1json/schema"
        );
        assertThat(responseSchema.isMissingNode())
                .as("response %s application/json schema", responseStatus)
                .isFalse();

        String schemaReference = responseSchema.path("$ref").asText();
        assertThat(schemaReference)
                .as("response %s schema reference", responseStatus)
                .startsWith("#/components/schemas/");

        String schemaName = schemaReference.substring(
                "#/components/schemas/".length()
        );
        JsonNode envelopeProperties = document.at(
                "/components/schemas/" + schemaName + "/properties"
        );
        assertThat(envelopeProperties.isMissingNode())
                .as("response %s envelope properties", responseStatus)
                .isFalse();
        assertThat(envelopeProperties.has("success")).isTrue();
        assertThat(envelopeProperties.has("code")).isTrue();
        assertThat(envelopeProperties.has("message")).isTrue();
        assertThat(envelopeProperties.has("data")).isTrue();
        assertThat(envelopeProperties.has("timestamp")).isTrue();

        if (expectedDataSchema != null) {
            assertThat(envelopeProperties.path("data").path("$ref").asText())
                    .as("response %s envelope data schema", responseStatus)
                    .isEqualTo(
                            "#/components/schemas/" + expectedDataSchema
                    );
        }
    }

    private JsonNode resolveReferencedSchema(
            JsonNode document,
            JsonNode schemaNode
    ) {
        String reference = schemaNode.path("$ref").asText();
        if (reference.isBlank()) {
            for (String composition : List.of("allOf", "anyOf", "oneOf")) {
                for (JsonNode candidate : schemaNode.path(composition)) {
                    reference = candidate.path("$ref").asText();
                    if (!reference.isBlank()) {
                        break;
                    }
                }
                if (!reference.isBlank()) {
                    break;
                }
            }
        }
        assertThat(reference).startsWith("#/components/schemas/");
        return document.at(reference.substring(1));
    }

    @Test
    void ChatOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(openApiDocument)
                .contains("/api/v1/studies/{studyId}/chat/messages")
                .contains("/api/v1/studies/{studyId}/chat/read")
                .contains("/api/v1/studies/{studyId}/chat/unread-count")
                .contains("bearerAuth")
                .contains("ChatMessageListResponse")
                .contains("ChatReadResponse")
                .contains("ChatUnreadCountResponse");
    }

    @Test
    void SttCreateOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1stt/post"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/202").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/409").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/410").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/500").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");

        JsonNode requestProperties = document.at(
                "/components/schemas/SttCreateRequest/properties"
        );
        assertThat(requestProperties.has("audioFileId")).isTrue();
        assertThat(requestProperties.has("checklistItemId")).isTrue();
        assertThat(requestProperties.has("clientRequestId")).isTrue();

        JsonNode responseProperties = document.at(
                "/components/schemas/SttCreateResponse/properties"
        );
        assertThat(responseProperties.has("sttId")).isTrue();
        assertThat(responseProperties.has("status")).isTrue();
        assertThat(responseProperties.has("sourceId")).isTrue();
        assertThat(responseProperties.has("objectKey")).isFalse();
    }

    @Test
    void SttStatusOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit"
                        + "~1stt~1{sttId}/get"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/400").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/403").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/500").isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");

        JsonNode examples = operation.at(
                "/responses/200/content/application~1json/examples"
        );
        assertThat(examples.has("PROCESSING")).isTrue();
        assertThat(examples.has("DONE")).isTrue();
        assertThat(examples.has("FAILED")).isTrue();
        assertThat(examples.at("/PROCESSING/value/timestamp").asText())
                .isEqualTo("2026-07-25T14:25:04+09:00");

        JsonNode responseSchema = operation.at(
                "/responses/200/content/application~1json/schema"
        );
        assertThat(responseSchema.at("/$ref").asText())
                .isEqualTo(
                        "#/components/schemas/ApiResponseSttStatusResponse"
                );

        JsonNode responseProperties = document.at(
                "/components/schemas/SttStatusResponse/properties"
        );
        assertThat(responseProperties.has("sttId")).isTrue();
        assertThat(responseProperties.has("sourceId")).isTrue();
        assertThat(responseProperties.has("textContent")).isTrue();
        assertThat(responseProperties.has("retryable")).isTrue();
        assertThat(responseProperties.has("audioFileId")).isFalse();
        assertThat(responseProperties.has("objectKey")).isFalse();
        assertThat(responseProperties.has("s3Key")).isFalse();
        assertThat(responseProperties.at("/sourceId/type").toString())
                .contains("null");
        assertThat(responseProperties.at("/textContent/type").toString())
                .contains("null");
        assertThat(responseProperties.at("/failReason/type").toString())
                .contains("null");
        assertThat(responseProperties.at("/completedAt/type").toString())
                .contains("null");
    }

    @Test
    void PostDetailOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1posts~1{postId}/get"
        );
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("게시글 상세 조회");
        assertThat(operation.toString()).contains("bearerAuth");

        JsonNode properties = document.at(
                "/components/schemas/PostDetailResponse/properties"
        );
        assertThat(properties.has("originalAvailable")).isTrue();
        assertThat(properties.has("attachments")).isTrue();
        assertThat(properties.has("permissions")).isTrue();
        assertThat(properties.has("hotScore")).isTrue();
        assertThat(openApiDocument)
                .doesNotContain("\"s3Key\"");
    }

    @Test
    void PostLikeOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1posts~1{postId}~1like/put"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("게시글 좋아요");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(operation, "200", "400", "401", "404", "500");
        assertThat(operation.toString())
                .contains("ApiResponsePostLikeResponse");

        JsonNode properties = document.at(
                "/components/schemas/PostLikeResponse/properties"
        );
        assertThat(properties.has("postId")).isTrue();
        assertThat(properties.has("likedByMe")).isTrue();
        assertThat(properties.has("likeCount")).isTrue();
        assertThat(properties.has("hotScore")).isTrue();
        assertThat(properties.has("hotRank")).isTrue();
        assertThat(properties.has("likedAt")).isTrue();
    }

    @Test
    void PostUnlikeOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1posts~1{postId}~1like/delete"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("게시글 좋아요 해제");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(operation, "200", "400", "401", "404", "500");
        assertThat(operation.toString())
                .contains("ApiResponsePostUnlikeResponse");

        JsonNode properties = document.at(
                "/components/schemas/PostUnlikeResponse/properties"
        );
        assertThat(properties.has("postId")).isTrue();
        assertThat(properties.has("likedByMe")).isTrue();
        assertThat(properties.has("likeCount")).isTrue();
        assertThat(properties.has("hotScore")).isTrue();
        assertThat(properties.has("hotRank")).isTrue();
        assertThat(properties.has("unlikedAt")).isTrue();
    }

    @Test
    void CommentCreateOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1posts~1{postId}~1comments/post"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(operation, "201", "400", "401", "404", "500");
        assertThat(operation.toString())
                .contains("ApiResponseCommentCreateResponse");

        JsonNode commentProperties = document.at(
                "/components/schemas/CommentResponse/properties"
        );
        assertThat(commentProperties.has("commentId")).isTrue();
        assertThat(commentProperties.has("author")).isTrue();
        assertThat(commentProperties.has("isPostAuthor")).isTrue();

        JsonNode metricsProperties = document.at(
                "/components/schemas/CommentPostMetricsResponse/properties"
        );
        assertThat(metricsProperties.has("commentCount")).isTrue();
        assertThat(metricsProperties.has("isHot")).isTrue();
        assertThat(metricsProperties.has("hotScore")).isTrue();
    }

    @Test
    void CommentUpdateOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1comments~1{commentId}/patch"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("댓글 수정");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.toString())
                .contains("ApiResponseCommentResponse");

        JsonNode requestProperties = document.at(
                "/components/schemas/CommentUpdateRequest/properties"
        );
        assertThat(requestProperties.size()).isEqualTo(1);
        assertThat(requestProperties.has("content")).isTrue();
    }

    @Test
    void CommentDeleteOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1comments~1{commentId}/delete"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("댓글 삭제");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.toString())
                .contains("ApiResponseCommentDeleteResponse");

        JsonNode properties = document.at(
                "/components/schemas/CommentDeleteResponse/properties"
        );
        assertThat(properties.size()).isEqualTo(5);
        assertThat(properties.has("commentId")).isTrue();
        assertThat(properties.has("postId")).isTrue();
        assertThat(properties.has("deletedAt")).isTrue();
        assertThat(properties.has("postAvailable")).isTrue();
        assertThat(properties.has("postMetrics")).isTrue();
    }

    @Test
    void PostDeleteOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1posts~1{postId}/delete"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("게시글 삭제");
        assertThat(operation.toString()).contains("bearerAuth");
        assertThat(operation.at("/responses/200").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/400").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/401").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/403").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/404").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/409").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/500").isMissingNode())
                .isFalse();

        JsonNode properties = document.at(
                "/components/schemas/PostDeleteResponse/properties"
        );
        assertThat(properties.size()).isEqualTo(2);
        assertThat(properties.has("postId")).isTrue();
        assertThat(properties.has("deletedAt")).isTrue();
        assertThat(properties.has("title")).isFalse();
        assertThat(properties.has("attachments")).isFalse();
    }

    @Test
    void ChecklistGenerateAndDetailOperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode generate = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1checklist~1generate/post"
        );
        JsonNode detail = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1checklist/get"
        );

        assertThat(generate.isMissingNode()).isFalse();
        assertThat(detail.isMissingNode()).isFalse();
        assertThat(generate.at("/summary").asText())
                .isEqualTo("개인 맞춤 체크리스트 생성");
        assertThat(detail.at("/summary").asText())
                .isEqualTo("내 체크리스트 조회");
        assertThat(generate.toString()).contains("bearerAuth");
        assertThat(detail.toString()).contains("bearerAuth");
        assertThat(generate.at("/responses/201").isMissingNode()).isFalse();
        assertThat(generate.at("/responses/200").isMissingNode()).isFalse();
        assertThat(detail.at("/responses/200").isMissingNode()).isFalse();

        assertThat(openApiDocument)
                .contains("ChecklistGenerateResponse")
                .contains("ChecklistDetailResponse");
    }

    @Test
    void FieldVisitBe015OperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        assertThat(document.at("/components/securitySchemes/bearerAuth").isMissingNode()).isFalse();

        JsonNode answers = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1checklist~1answers/put"
        );
        JsonNode create = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1records/post"
        );
        JsonNode list = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1records/get"
        );
        JsonNode patch = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1records~1{recordId}/patch"
        );
        JsonNode delete = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1records~1{recordId}/delete"
        );

        assertThat(answers.isMissingNode()).isFalse();
        assertThat(create.isMissingNode()).isFalse();
        assertThat(list.isMissingNode()).isFalse();
        assertThat(patch.isMissingNode()).isFalse();
        assertThat(delete.isMissingNode()).isFalse();

        assertThat(answers.at("/security").toString()).contains("bearerAuth");
        assertThat(answers.at("/parameters").toString()).contains("studyId");
        assertThat(answers.at("/requestBody").isMissingNode()).isFalse();
        for (String code : List.of("200", "400", "401", "403", "404", "409")) {
            assertThat(answers.at("/responses/" + code).isMissingNode()).isFalse();
        }

        JsonNode createReq = document.at(
                "/components/schemas/FieldRecordCreateRequest/properties"
        );
        assertThat(createReq.has("sourceType")).isTrue();
        assertThat(createReq.at("/clientRequestId/format").asText()).isEqualTo("uuid");
        assertThat(createReq.at("/checklistItemId/minimum").asInt()).isEqualTo(1);
        assertThat(createReq.at("/photoFileId/minimum").asInt()).isEqualTo(1);
        assertThat(createReq.at("/textContent/maxLength").asInt()).isEqualTo(2000);
        assertThat(openApiDocument).contains("TEXT").contains("PHOTO");
        assertThat(create.at("/responses/201").isMissingNode()).isFalse();
        assertThat(create.at("/responses/200").isMissingNode()).isFalse();
        for (String code : List.of("400", "401", "403", "404", "409")) {
            assertThat(create.at("/responses/" + code).isMissingNode()).isFalse();
        }

        JsonNode createRes = document.at(
                "/components/schemas/FieldRecordCreateResponse/properties"
        );
        assertThat(createRes.has("studyId")).isTrue();
        assertThat(createRes.has("sessionId")).isTrue();
        assertThat(createRes.has("record")).isTrue();
        assertThat(createRes.has("itemRecordCount")).isTrue();
        assertThat(openApiDocument)
                .contains("sourceId")
                .contains("canEdit")
                .contains("canDelete")
                .contains("itemRecordCount")
                .contains("author");

        assertThat(list.at("/security").toString()).contains("bearerAuth");
        String listParams = list.at("/parameters").toString();
        assertThat(listParams)
                .contains("checklistItemId")
                .contains("sourceType")
                .contains("mineOnly")
                .contains("cursor")
                .contains("size");
        assertThat(listParams).containsAnyOf("\"default\":\"true\"", "\"default\":true");
        assertThat(listParams).containsAnyOf("\"default\":\"20\"", "\"default\":20");

        JsonNode updateReq = document.at(
                "/components/schemas/FieldRecordUpdateRequest/properties"
        );
        assertThat(updateReq.at("/textContent/maxLength").asInt()).isEqualTo(2000);
        assertThat(updateReq.at("/checklistItemId/minimum").asInt()).isEqualTo(1);
        assertThat(updateReq.at("/photoFileId/minimum").asInt()).isEqualTo(1);
        assertThat(patch.at("/parameters").toString()).contains("recordId");
        for (String code : List.of("200", "400", "401", "403", "404", "409")) {
            assertThat(patch.at("/responses/" + code).isMissingNode()).isFalse();
        }

        JsonNode mutation = document.at(
                "/components/schemas/FieldRecordMutationResponse/properties"
        );
        assertThat(mutation.has("sourceId")).isTrue();
        assertThat(mutation.has("author")).isTrue();
        assertThat(mutation.has("itemRecordCount")).isTrue();
        assertThat(mutation.has("createdAt")).isTrue();
        assertThat(mutation.has("updatedAt")).isTrue();
        assertThat(mutation.has("deleted")).isTrue();
        assertThat(mutation.has("deletedAt")).isTrue();

        assertThat(delete.at("/parameters").toString()).contains("recordId");
        for (String code : List.of("200", "401", "403", "404", "409")) {
            assertThat(delete.at("/responses/" + code).isMissingNode()).isFalse();
        }

        assertThat(openApiDocument)
                .contains("ChecklistAnswerSaveResponse")
                .contains("FieldRecordCreateResponse")
                .contains("FieldRecordListResponse")
                .contains("categoryProgress")
                .contains("completedAt");
    }

    @Test
    void FieldVisitBe014OperationsArePublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        assertThat(document.at("/components/securitySchemes/bearerAuth").isMissingNode())
                .isFalse();

        JsonNode status = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit/get"
        );
        JsonNode start = document.at(
                "/paths/~1api~1v1~1studies~1{studyId}~1field-visit~1start/post"
        );

        assertThat(status.isMissingNode()).isFalse();
        assertThat(start.isMissingNode()).isFalse();
        assertThat(status.at("/security").toString()).contains("bearerAuth");
        assertThat(start.at("/security").toString()).contains("bearerAuth");

        assertResponses(status, "200", "400", "401", "403", "404", "500");
        assertJsonEnvelope(
                document,
                status,
                "200",
                "FieldVisitStatusResponse"
        );
        for (String code : List.of("400", "401", "403", "404", "500")) {
            assertJsonEnvelope(document, status, code, null);
        }
        assertResponses(
                start,
                "201",
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "422",
                "500"
        );
        assertJsonEnvelope(
                document,
                start,
                "201",
                "FieldVisitStartResponse"
        );
        assertJsonEnvelope(
                document,
                start,
                "200",
                "FieldVisitStartResponse"
        );
        for (String code : List.of(
                "400", "401", "403", "404", "409", "422", "500"
        )) {
            assertJsonEnvelope(document, start, code, null);
        }

        JsonNode startReq = document.at(
                "/components/schemas/FieldVisitStartRequestBody/properties"
        );
        assertThat(startReq.has("latitude")).isTrue();
        assertThat(startReq.has("longitude")).isTrue();
        assertThat(startReq.has("scheduleOverrideConfirmed")).isTrue();
        assertThat(startReq.at("/scheduleOverrideConfirmed/default").asBoolean()).isFalse();
        assertThat(startReq.at("/clientRequestId/format").asText()).isEqualTo("uuid");
        assertThat(startReq.at("/latitude/minimum").asDouble()).isEqualTo(-90.0);
        assertThat(startReq.at("/latitude/maximum").asDouble()).isEqualTo(90.0);
        assertThat(startReq.at("/longitude/minimum").asDouble()).isEqualTo(-180.0);
        assertThat(startReq.at("/longitude/maximum").asDouble()).isEqualTo(180.0);

        JsonNode startRes = document.at(
                "/components/schemas/FieldVisitStartResponse/properties"
        );
        assertThat(startRes.has("studyId")).isTrue();
        assertThat(startRes.has("apartmentId")).isTrue();
        assertThat(startRes.has("distanceMeters")).isTrue();
        assertThat(startRes.has("allowedRadiusMeters")).isTrue();
        assertThat(startRes.has("session")).isTrue();
        assertThat(startRes.has("participant")).isTrue();
        assertThat(startRes.has("checklistGenerated")).isTrue();

        JsonNode statusRes = document.at(
                "/components/schemas/FieldVisitStatusResponse/properties"
        );
        assertThat(statusRes.has("studyId")).isTrue();
        assertThat(statusRes.has("status")).isTrue();
        assertThat(statusRes.has("session")).isTrue();
        assertThat(statusRes.has("participant")).isTrue();
        assertThat(statusRes.has("checklistProgress")).isTrue();
        assertThat(statusRes.has("permissions")).isTrue();
        assertThat(statusRes.has("closeVote")).isTrue();
    }

    @Test
    void ReportStatusOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1reports~1{reportId}~1status/get"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("리포트 생성 상태 조회");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "500"
        );
        assertThat(operation.at("/parameters").toString())
                .contains("reportId")
                .contains("minimum");
        assertThat(operation.toString())
                .contains("ApiResponseReportStatusResponse");

        JsonNode properties = document.at(
                "/components/schemas/ReportStatusResponse/properties"
        );
        for (String property : List.of(
                "reportId",
                "status",
                "progressRate",
                "progressStage",
                "progressMessage",
                "detailAvailable",
                "retryAvailable",
                "failReason",
                "createdAt",
                "completedAt",
                "updatedAt"
        )) {
            assertThat(properties.has(property)).isTrue();
        }
    }

    @Test
    void ReportRetryOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1reports~1{reportId}~1retry/post"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("실패한 리포트 재생성 요청");
        assertThat(operation.has("requestBody")).isFalse();
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(
                operation,
                "200",
                "202",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.at("/parameters").toString())
                .contains("reportId")
                .contains("minimum");
        assertThat(operation.toString())
                .contains("ApiResponseReportRetryResponse");

        JsonNode properties = document.at(
                "/components/schemas/ReportRetryResponse/properties"
        );
        for (String property : List.of(
                "reportId",
                "status",
                "progressRate",
                "progressStage",
                "retryRequestedAt",
                "statusApi"
        )) {
            assertThat(properties.has(property)).isTrue();
        }
    }

    @Test
    void ReportDetailOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1reports~1{reportId}/get"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("리포트 상세 조회");
        assertThat(operation.toString()).contains("bearerAuth");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.at("/parameters").toString())
                .contains("reportId")
                .contains("minimum");
         assertThat(operation.toString())
                 .contains("ApiResponseReportDetailResponse");
        for (String errorStatus : List.of(
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        )) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }

        JsonNode properties = document.at(
                "/components/schemas/ReportDetailResponse/properties"
        );
         for (String property : List.of(
                "reportId",
                "status",
                "progressStage",
                "title",
                "summary",
                "apartment",
                "study",
                "metrics",
                "topPositiveFeatures",
                "topCautionFeatures",
                "commonOpinions",
                "conflictingOpinions",
                "categories",
                "viewer",
                "favoritedByMe",
                "favoriteCount",
                "postId",
                "completedAt",
                "updatedAt"
        )) {
             assertThat(properties.has(property)).isTrue();
         }

        JsonNode apartmentProperties = document.at(
                "/components/schemas/"
                        + "ReportDetailApartmentSummary/properties"
        );
        for (String property : List.of(
                "apartmentId",
                "name",
                "address",
                "householdCount",
                "completionYearMonth",
                "parkingSpaceCount"
        )) {
            assertThat(apartmentProperties.has(property)).isTrue();
        }
      }

    @Test
    void ReportEvidenceListOperationIsPublished() throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1reports~1{reportId}~1evidences/get"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("리포트 근거 목록 조회");
        assertThat(operation.toString())
                .contains("bearerAuth")
                .contains("ApiResponseReportEvidenceListResponse");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        String parameters = operation.at("/parameters").toString();
        for (String parameter : List.of(
                "reportId",
                "sourceType",
                "category",
                "sourceIds",
                "cursor",
                "size"
        )) {
            assertThat(parameters).contains(parameter);
        }
        assertThat(parameters)
                .contains("TEXT")
                .contains("STT")
                .contains("PHOTO")
                .contains("maximum")
                .contains("default");

        JsonNode properties = document.at(
                "/components/schemas/ReportEvidenceListResponse/properties"
        );
        for (String property : List.of(
                "reportId",
                "filters",
                "content",
                "summary",
                "nextCursor",
                "hasNext"
        )) {
            assertThat(properties.has(property)).isTrue();
        }
    }

    @Test
    void ReportEvidenceDetailOperationPublishesRawSourceContract()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1api~1v1~1reports~1{reportId}~1evidences~1"
                        + "{sourceId}/get"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("리포트 근거 원문 조회");
        assertThat(operation.toString())
                .contains("bearerAuth")
                .contains("ApiResponseReportEvidenceDetailResponse")
                .contains("AI에 사용되지 않은 원문")
                .contains("usedIn");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "410",
                "503",
                "500"
        );
        assertThat(operation.at("/parameters").toString())
                .contains("reportId")
                .contains("sourceId")
                .contains("minimum");

        JsonNode properties = document.at(
                "/components/schemas/"
                        + "ReportEvidenceDetailResponse/properties"
        );
        for (String property : List.of(
                "reportId",
                "sourceId",
                "sourceType",
                "category",
                "checklistItem",
                "participantLabel",
                "textContent",
                "media",
                "sttStatus",
                "usedIn",
                "recordedAt"
        )) {
            assertThat(properties.has(property))
                    .as("ReportEvidenceDetailResponse.%s", property)
                    .isTrue();
        }

        JsonNode media = document.at(
                "/components/schemas/ReportEvidenceDetailMedia/properties"
        );
        for (String property : List.of(
                "available",
                "fileId",
                "originalName",
                "contentType",
                "sizeBytes",
                "accessUrl",
                "expiresAt"
        )) {
            assertThat(media.has(property)).isTrue();
        }
    }

    @Test
    void ReportAcquireOperationUsesDedicatedInternalSecurityScheme()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1internal~1v1~1reports~1acquire/post"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("AI 리포트 처리권 획득");
        assertThat(operation.at("/security").toString())
                .contains("internalBearerAuth")
                .doesNotContain("bearerAuth\"");
        assertResponses(operation, "200", "400", "401", "403", "500");
        assertThat(operation.at(
                "/responses/200/content/application~1json/schema/$ref"
        ).asText()).isEqualTo(
                "#/components/schemas/ApiResponseReportAcquireResponse"
        );
        for (String errorStatus : List.of("400", "401", "403", "500")) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }
        assertThat(document.at(
                "/components/securitySchemes/internalBearerAuth"
        ).isMissingNode()).isFalse();

        JsonNode request = document.at(
                "/components/schemas/ReportAcquireRequest/properties"
        );
        for (String property : List.of(
                "studyId",
                "sessionId",
                "apartmentId",
                "occurredAt"
        )) {
            assertThat(request.has(property)).isTrue();
        }
        assertThat(request.at("/studyId/minimum").asLong()).isEqualTo(1L);
        assertThat(request.at("/sessionId/minimum").asLong()).isEqualTo(1L);
        assertThat(request.at("/apartmentId/minimum").asLong()).isEqualTo(1L);
        assertThat(request.at("/occurredAt/format").asText())
                .isEqualTo("date-time");

        JsonNode response = document.at(
                "/components/schemas/ReportAcquireResponse/properties"
        );
        for (String property : List.of(
                "status",
                "reportId",
                "processingToken",
                "processingAttempt",
                "leaseExpiresAt",
                "retryAfterSeconds"
        )) {
            assertThat(response.has(property)).isTrue();
        }
    }

    @Test
    void ReportInputOperationPublishesExactInternalContract()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1internal~1v1~1reports~1{reportId}~1input/get"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("AI 리포트 정규화 입력 원본 조회");
        assertThat(operation.at("/security").toString())
                .contains("internalBearerAuth")
                .doesNotContain("bearerAuth\"");
        assertResponses(
                operation,
                "200",
                "400",
                "401",
                "403",
                "404",
                "500"
        );
        assertThat(operation.at("/requestBody").isMissingNode()).isTrue();
        assertThat(operation.at("/parameters/0/name").asText())
                .isEqualTo("reportId");
        assertThat(operation.at("/parameters/0/in").asText())
                .isEqualTo("path");
        assertThat(operation.at("/parameters/0/required").asBoolean())
                .isTrue();
        assertThat(operation.at("/parameters/0/schema/type").asText())
                .isEqualTo("integer");
        assertThat(operation.at("/parameters/0/schema/format").asText())
                .isEqualTo("int64");
        assertThat(operation.at("/parameters/0/schema/minimum").asLong())
                .isEqualTo(1L);
        assertThat(operation.at(
                "/responses/200/content/application~1json/schema/$ref"
        ).asText()).isEqualTo(
                "#/components/schemas/ApiResponseReportInputResponse"
        );
        for (String errorStatus : List.of(
                "400",
                "401",
                "403",
                "404",
                "500"
        )) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }

        JsonNode response = document.at(
                "/components/schemas/ReportInputResponse/properties"
        );
        assertThat(response.size()).isEqualTo(14);
        for (String property : List.of(
                "schemaVersion",
                "reportId",
                "studyId",
                "apartmentId",
                "fieldSessionId",
                "sessionStatus",
                "sessionStartedAt",
                "sessionEndedAt",
                "snapshotAt",
                "participants",
                "checklistItems",
                "authoritativeSourceIds",
                "fieldRecords",
                "incompleteSttJobs"
        )) {
            assertThat(response.has(property)).isTrue();
        }
        assertThat(response.at("/schemaVersion/type").asText())
                .isEqualTo("integer");
        assertThat(response.at("/authoritativeSourceIds/items/format").asText())
                .isEqualTo("int64");

        JsonNode participant = resolveReferencedSchema(
                document,
                response.at("/participants/items")
        ).path("properties");
        assertThat(participant.size()).isEqualTo(5);
        for (String property : List.of(
                "fieldParticipantId",
                "memberId",
                "status",
                "startedAt",
                "endedAt"
        )) {
            assertThat(participant.has(property)).isTrue();
        }

        JsonNode checklistItem = resolveReferencedSchema(
                document,
                response.at("/checklistItems/items")
        ).path("properties");
        assertThat(checklistItem.size()).isEqualTo(10);
        for (String property : List.of(
                "checklistId",
                "checklistItemId",
                "memberId",
                "fallback",
                "category",
                "title",
                "subtitle",
                "displayOrder",
                "completed",
                "completedAt"
        )) {
            assertThat(checklistItem.has(property)).isTrue();
        }

        JsonNode fieldRecord = resolveReferencedSchema(
                document,
                response.at("/fieldRecords/items")
        ).path("properties");
        assertThat(fieldRecord.size()).isEqualTo(11);
        for (String property : List.of(
                "sourceId",
                "sessionId",
                "checklistItemId",
                "authorId",
                "sourceType",
                "textContent",
                "sttStatus",
                "photoFile",
                "deletedAt",
                "recordedAt",
                "updatedAt"
        )) {
            assertThat(fieldRecord.has(property)).isTrue();
        }

        JsonNode photoFile = resolveReferencedSchema(
                document,
                fieldRecord.path("photoFile")
        ).path("properties");
        assertThat(photoFile.size()).isEqualTo(6);
        for (String property : List.of(
                "fileId",
                "contentType",
                "sizeBytes",
                "uploadStatus",
                "expiresAt",
                "deletedAt"
        )) {
            assertThat(photoFile.has(property)).isTrue();
        }

        JsonNode incompleteSttJob = resolveReferencedSchema(
                document,
                response.at("/incompleteSttJobs/items")
        ).path("properties");
        assertThat(incompleteSttJob.size()).isEqualTo(7);
        for (String property : List.of(
                "sttId",
                "memberId",
                "checklistItemId",
                "status",
                "retryable",
                "failCode",
                "requestedAt"
        )) {
            assertThat(incompleteSttJob.has(property)).isTrue();
        }

        assertThat(response.toString())
                .doesNotContain("processingToken")
                .doesNotContain("processingAttempt");
    }

    @Test
    void ReportProgressOperationPublishesExactInternalContract()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1internal~1v1~1reports~1{reportId}~1progress/patch"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("AI 리포트 진행 단계 저장");
        assertThat(operation.at("/security").toString())
                .contains("internalBearerAuth")
                .doesNotContain("bearerAuth\"");
        assertResponses(
                operation,
                "204",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.at("/responses/204/content").isMissingNode())
                .isTrue();
        for (String errorStatus : List.of(
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        )) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }

        JsonNode request = document.at(
                "/components/schemas/ReportProgressRequest/properties"
        );
        for (String property : List.of(
                "processingToken",
                "processingAttempt",
                "stage"
        )) {
            assertThat(request.has(property)).isTrue();
        }
        assertThat(request.at("/processingAttempt/minimum").asLong())
                .isEqualTo(1L);

        JsonNode stageSchema = request.at("/stage");
        assertThat(stageSchema.toString())
                .contains("RECORD_COLLECTION")
                .contains("STT_VALIDATION")
                .contains("NORMALIZATION")
                .contains("REPORT_GENERATION")
                .contains("EVIDENCE_MAPPING")
                .contains("RESULT_SAVING")
                .doesNotContain("COMPLETED");
    }

    @Test
    void ReportCompleteOperationPublishesExactInternalContract()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1internal~1v1~1reports~1{reportId}~1complete/put"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("AI 리포트 결과·근거 완료 저장");
        assertThat(operation.at("/security").toString())
                .contains("internalBearerAuth")
                .doesNotContain("bearerAuth\"");
        assertResponses(
                operation,
                "204",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.at("/responses/204/content").isMissingNode())
                .isTrue();
        for (String errorStatus : List.of(
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        )) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }

        assertThat(operation.at("/parameters/0/name").asText())
                .isEqualTo("reportId");
        assertThat(operation.at("/parameters/0/in").asText())
                .isEqualTo("path");
        assertThat(operation.at("/parameters/0/required").asBoolean())
                .isTrue();
        assertThat(operation.at("/parameters/0/schema/type").asText())
                .isEqualTo("integer");
        assertThat(operation.at("/parameters/0/schema/format").asText())
                .isEqualTo("int64");
        assertThat(operation.at("/parameters/0/schema/minimum").asLong())
                .isEqualTo(1L);
        assertThat(operation.at("/requestBody/required").asBoolean())
                .isTrue();
        assertThat(operation.at(
                "/requestBody/content/application~1json/schema/$ref"
        ).asText()).isEqualTo(
                "#/components/schemas/ReportCompleteRequest"
        );

        JsonNode request = document.at(
                "/components/schemas/ReportCompleteRequest"
        );
        assertThat(request.path("required").size()).isEqualTo(4);
        for (String requiredProperty : List.of(
                "processingToken",
                "processingAttempt",
                "generationResult",
                "evidenceResult"
        )) {
            assertThat(request.path("required").toString())
                    .contains("\"" + requiredProperty + "\"");
        }
        JsonNode requestProperties = request.path("properties");
        assertThat(requestProperties.at("/processingAttempt/minimum").asLong())
                .isEqualTo(1L);

        JsonNode generationResult = resolveReferencedSchema(
                document,
                requestProperties.path("generationResult")
        );
        for (String property : List.of(
                "title",
                "summary",
                "metrics",
                "topPositiveFeatures",
                "topCautionFeatures",
                "commonOpinions",
                "conflictingOpinions",
                "categories"
        )) {
            assertThat(generationResult.path("properties").has(property))
                    .isTrue();
        }

        JsonNode metrics = resolveReferencedSchema(
                document,
                generationResult.path("properties").path("metrics")
        ).path("properties");
        assertThat(metrics.at("/totalChecklistItemCount/minimum").asLong())
                .isZero();
        assertThat(metrics.at("/averageCompletionRate/minimum").asDouble())
                .isZero();
        assertThat(metrics.at("/averageCompletionRate/maximum").asDouble())
                .isEqualTo(100.0);

        JsonNode evidenceResult = resolveReferencedSchema(
                document,
                requestProperties.path("evidenceResult")
        );
        assertThat(evidenceResult.path("required").toString())
                .contains("\"claims\"");
        JsonNode claim = resolveReferencedSchema(
                document,
                evidenceResult.path("properties").path("claims").path("items")
        );
        for (String property : List.of(
                "claimKey",
                "claimType",
                "participantRefs",
                "evidences",
                "displayOrder"
        )) {
            assertThat(claim.path("properties").has(property)).isTrue();
        }
        assertThat(claim.path("properties").path("claimType").toString())
                .contains("FEATURE_POSITIVE")
                .contains("PARTICIPANT_OPINION");

        JsonNode evidence = resolveReferencedSchema(
                document,
                claim.path("properties").path("evidences").path("items")
        );
        assertThat(evidence.path("properties").path("sourceType").toString())
                .contains("TEXT")
                .contains("STT")
                .doesNotContain("PHOTO");
        JsonNode sourceId = evidence.path("properties").path("sourceId");
        assertThat(sourceId.path("type").asText()).isEqualTo("integer");
        assertThat(sourceId.path("format").asText()).isEqualTo("int64");
    }

    @Test
    void ReportFailOperationPublishesExactInternalContract()
            throws Exception {
        String openApiDocument = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = objectMapper.readTree(openApiDocument);
        JsonNode operation = document.at(
                "/paths/~1internal~1v1~1reports~1{reportId}~1fail/put"
        );

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/summary").asText())
                .isEqualTo("AI 리포트 실패 정보 저장");
        assertThat(operation.at("/security").toString())
                .contains("internalBearerAuth")
                .doesNotContain("bearerAuth\"");
        assertResponses(
                operation,
                "204",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        );
        assertThat(operation.at("/responses/204/content").isMissingNode())
                .isTrue();
        for (String errorStatus : List.of(
                "400",
                "401",
                "403",
                "404",
                "409",
                "500"
        )) {
            assertThat(operation.at(
                    "/responses/" + errorStatus
                            + "/content/application~1json/schema/$ref"
            ).asText()).isEqualTo("#/components/schemas/ApiResponse");
        }

        assertThat(operation.at("/parameters/0/name").asText())
                .isEqualTo("reportId");
        assertThat(operation.at("/parameters/0/in").asText())
                .isEqualTo("path");
        assertThat(operation.at("/parameters/0/required").asBoolean())
                .isTrue();
        assertThat(operation.at("/parameters/0/schema/type").asText())
                .isEqualTo("integer");
        assertThat(operation.at("/parameters/0/schema/format").asText())
                .isEqualTo("int64");
        assertThat(operation.at("/parameters/0/schema/minimum").asLong())
                .isEqualTo(1L);
        assertThat(operation.at("/requestBody/required").asBoolean())
                .isTrue();
        assertThat(operation.at(
                "/requestBody/content/application~1json/schema/$ref"
        ).asText()).isEqualTo("#/components/schemas/ReportFailRequest");

        JsonNode request = document.at(
                "/components/schemas/ReportFailRequest"
        );
        assertThat(request.path("required").size()).isEqualTo(6);
        for (String requiredProperty : List.of(
                "processingToken",
                "processingAttempt",
                "failedStage",
                "errorCode",
                "message",
                "retryable"
        )) {
            assertThat(request.path("required").toString())
                    .contains("\"" + requiredProperty + "\"");
        }

        JsonNode properties = request.path("properties");
        assertThat(properties.size()).isEqualTo(6);
        assertThat(properties.at("/processingToken/minLength").asLong())
                .isEqualTo(1L);
        assertThat(properties.at("/processingAttempt/minimum").asLong())
                .isEqualTo(1L);
        assertThat(properties.at("/message/minLength").asLong())
                .isEqualTo(1L);
        assertThat(properties.at("/message/maxLength").asLong())
                .isEqualTo(500L);
        assertThat(properties.at("/retryable/type").asText())
                .isEqualTo("boolean");

        JsonNode failedStage = properties.path("failedStage");
        assertThat(failedStage.toString())
                .contains("RECORD_COLLECTION")
                .contains("STT_VALIDATION")
                .contains("NORMALIZATION")
                .contains("REPORT_GENERATION")
                .contains("EVIDENCE_MAPPING")
                .contains("RESULT_SAVING")
                .doesNotContain("COMPLETED");

        JsonNode errorCode = properties.path("errorCode");
        assertThat(errorCode.path("maxLength").asLong()).isEqualTo(100L);
        assertThat(errorCode.toString())
                .contains("NORMALIZATION_FAILED")
                .contains("PROVIDER_FAILED")
                .contains("UNKNOWN_FAILURE");
    }
}
