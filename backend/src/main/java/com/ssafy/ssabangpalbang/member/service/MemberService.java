package com.ssafy.ssabangpalbang.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberProfileUpdateRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicProfileSection;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.request.OnboardingRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileUpdateResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberPublicProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.OnboardingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.PublicProfileFollowingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.PublicProfileReportResponse;
import com.ssafy.ssabangpalbang.member.dto.response.PublicProfileStudyResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberPreferenceRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.service.MemberReviewSummaryReader;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private static final String MEMBER_NICKNAME_UNIQUE_CONSTRAINT =
            "member_nickname_key";
    private static final List<String> ALLOWED_PURPOSES = List.of(
            "RESIDENCE",
            "INVESTMENT",
            "STUDY"
    );
    private static final List<String> ALLOWED_AGE_GROUPS = List.of(
            "TEENS",
            "TWENTIES",
            "THIRTIES",
            "FORTIES",
            "FIFTIES",
            "SIXTIES_PLUS"
    );
    private static final List<String> ALLOWED_CHARACTER_IDS = List.of(
            "PALBANG",
            "PALBANG_RABBIT",
            "PALBANG_DOG"
    );
    private static final List<String> ALLOWED_MARITAL_STATUSES = List.of(
            "SINGLE",
            "MARRIED"
    );
    private static final List<String> ALLOWED_PRIORITIES = List.of(
            "TRANSPORT",
            "SAFETY",
            "EDUCATION",
            "COMMERCIAL",
            "WALKABILITY",
            "GREEN_SPACE",
            "PARKING",
            "NOISE"
    );

    private final MemberRepository memberRepository;
    private final MemberPreferenceRepository memberPreferenceRepository;
    private final StudyRepository studyRepository;
    private final ReportRepository reportRepository;
    private final MemberReviewSummaryReader memberReviewSummaryReader;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveMember(member);

        MemberPreference preference = memberPreferenceRepository
                .findByMemberId(memberId)
                .orElse(null);
        long studyCount = memberRepository.countAccessibleStudies(memberId);
        long reportCount = memberRepository.countAccessibleDoneReports(
                memberId
        );
        long followingCount = memberRepository.countFollowing(memberId);
        long fieldVisitCompletedCount = fieldParticipantRepository
                .countByMemberIdAndStatus(
                        memberId,
                        FieldParticipantStatus.ENDED
                );
        MemberReviewSummaryResponse reviewSummary =
                memberReviewSummaryReader.read(memberId);

        return MemberProfileResponse.from(
                member,
                preference,
                studyCount,
                reportCount,
                followingCount,
                fieldVisitCompletedCount,
                reviewSummary,
                clock.instant()
        );
    }

    @Transactional(readOnly = true)
    public MemberPublicProfileResponse getPublicProfile(
            Long viewerId,
            Long targetMemberId,
            MemberPublicProfileSection section,
            MemberPublicStudyStatus studyStatus,
            int page,
            int size
    ) {
        Member viewer = memberRepository.findById(viewerId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveMember(viewer);

        boolean isMe = viewerId.equals(targetMemberId);
        Member targetMember = isMe
                ? viewer
                : memberRepository.findById(targetMemberId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.MEMBER_NOT_FOUND
                        ));
        validatePublicTarget(targetMember);

        MemberPreference preference = memberPreferenceRepository
                .findByMemberId(targetMemberId)
                .orElse(null);
        long participatingStudyCount = studyRepository
                .countPublicProfileStudies(targetMemberId);
        long reportCount = reportRepository.countPublicProfileReports(
                targetMemberId
        );
        long followingCount = memberRepository
                .countPublicProfileFollowings(targetMemberId);
        long fieldVisitCompletedCount = fieldParticipantRepository
                .countByMemberIdAndStatus(
                        targetMemberId,
                        FieldParticipantStatus.ENDED
                );
        MemberReviewSummaryResponse reviewSummary =
                memberReviewSummaryReader.read(targetMemberId);
        boolean isFollowing = !isMe && memberRepository.existsFollow(
                viewerId,
                targetMemberId
        );

        PageResponse<PublicProfileStudyResponse> studies = null;
        PageResponse<PublicProfileReportResponse> reports = null;
        PageResponse<PublicProfileFollowingResponse> followings = null;
        PageRequest pageable = PageRequest.of(page, size);

        switch (section) {
            case STUDIES -> studies = PageResponse.from(
                    studyRepository.findPublicProfileStudies(
                            targetMemberId,
                            studyStatus.name(),
                            pageable
                    ).map(PublicProfileStudyResponse::from)
            );
            case REPORTS -> reports = PageResponse.from(
                    reportRepository.findPublicProfileReports(
                            viewerId,
                            targetMemberId,
                            pageable
                    ).map(row -> {
                        ParsedPublicReport result = parsePublicReportResult(
                                row.getReportId(),
                                row.getResultJson()
                        );
                        return PublicProfileReportResponse.from(
                                row,
                                result.title(),
                                result.summary(),
                                result.analysisTags()
                        );
                    })
            );
            case FOLLOWINGS -> followings = PageResponse.from(
                    memberRepository.findPublicProfileFollowings(
                            viewerId,
                            targetMemberId,
                            pageable
                    ).map(row -> PublicProfileFollowingResponse.from(
                            row,
                            viewerId
                    ))
            );
        }

        return MemberPublicProfileResponse.from(
                targetMember,
                preference,
                participatingStudyCount,
                reportCount,
                followingCount,
                fieldVisitCompletedCount,
                reviewSummary,
                isMe,
                isFollowing,
                section,
                studies,
                reports,
                followings
        );
    }

    @Transactional
    public MemberProfileUpdateResponse updateMyProfile(
            Long memberId,
            MemberProfileUpdateRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveMember(member);
        if (!request.hasAnyField()) {
            throw new BusinessException(ErrorCode.MEMBER_PROFILE_UPDATE_EMPTY);
        }

        MemberPreference preference = memberPreferenceRepository
                .findByMemberId(memberId)
                .orElse(null);
        ValidatedProfileUpdate update = validateProfileUpdate(
                member,
                preference,
                request
        );

        member.updateProfile(
                update.nickname(),
                update.ageGroup(),
                update.ageGroupPublicAgreed(),
                update.selectedCharacterId()
        );

        if (request.hasPreferenceField()) {
            if (preference == null) {
                preference = new MemberPreference(memberId);
            }
            preference.updateProfile(
                    update.purpose(),
                    update.priorities(),
                    update.maritalStatus(),
                    update.hasVehicle(),
                    update.hasChildren()
            );
        }

        persistProfileUpdate(preference, request.hasPreferenceField());
        return MemberProfileUpdateResponse.from(
                member,
                preference,
                clock.instant()
        );
    }

    @Transactional
    public OnboardingResponse saveOnboarding(
            Long memberId,
            OnboardingRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateActiveMember(member);

        MemberPreference preference = memberPreferenceRepository
                .findByMemberId(memberId)
                .orElseGet(() -> new MemberPreference(memberId));

        ValidatedOnboarding onboarding = validate(request);
        preference.updateOnboarding(
                onboarding.purpose(),
                onboarding.maritalStatus(),
                onboarding.hasVehicle(),
                onboarding.hasChildren(),
                onboarding.priorities()
        );
        member.updateOnboardingProfile(
                onboarding.ageGroup(),
                onboarding.ageGroupPublicAgreed(),
                onboarding.selectedCharacterId()
        );

        MemberPreference savedPreference = memberPreferenceRepository.save(
                preference
        );
        return OnboardingResponse.from(member, savedPreference);
    }

    private ValidatedProfileUpdate validateProfileUpdate(
            Member member,
            MemberPreference preference,
            MemberProfileUpdateRequest request
    ) {
        String nickname = member.getNickname();
        if (request.hasNickname()) {
            nickname = requirePatchValue(
                    request.getNickname(),
                    "nickname"
            ).strip();
            if (nickname.isEmpty() || nickname.length() > 50) {
                throw invalidProfileField(
                        "nickname",
                        "닉네임은 1자 이상 50자 이하로 입력해 주세요."
                );
            }
            if (!nickname.equals(member.getNickname())
                    && memberRepository.existsByNicknameAndIdNot(
                            nickname,
                            member.getId()
                    )) {
                throw new BusinessException(
                        ErrorCode.AUTH_NICKNAME_DUPLICATED
                );
            }
        }

        String ageGroup = member.getAgeGroup();
        if (request.hasAgeGroup()) {
            ageGroup = requirePatchValue(
                    request.getAgeGroup(),
                    "ageGroup"
            );
            validateProfileAgeGroup(ageGroup);
        }

        boolean ageGroupPublicAgreed = member.isAgeGroupPublicAgreed();
        if (request.hasAgeGroupPublicAgreed()) {
            ageGroupPublicAgreed = requirePatchValue(
                    request.getAgeGroupPublicAgreed(),
                    "ageGroupPublicAgreed"
            );
        }

        String purpose = preference == null
                ? null
                : preference.getPurpose();
        if (request.hasPurpose()) {
            purpose = normalizeRequiredPatchText(
                    request.getPurpose(),
                    "purpose",
                    50
            );
            validateAllowed(purpose, ALLOWED_PURPOSES, "purpose");
        }

        String selectedCharacterId = member.getSelectedCharacterId();
        if (request.hasSelectedCharacterId()) {
            selectedCharacterId = requirePatchValue(
                    request.getSelectedCharacterId(),
                    "selectedCharacterId"
            );
            validateProfileCharacter(selectedCharacterId);
        }

        List<String> priorities = preference == null
                ? List.of()
                : preference.getPriorities();
        if (request.hasPriorities()) {
            List<String> requestedPriorities = requirePatchValue(
                    request.getPriorities(),
                    "priorities"
            );
            if (requestedPriorities.isEmpty()
                    || requestedPriorities.size() > 4) {
                throw invalidProfileField(
                        "priorities",
                        "우선순위는 1개 이상 4개 이하로 선택해 주세요."
                );
            }
            priorities = requestedPriorities.stream()
                    .map(this::normalizePriority)
                    .toList();
            if (new HashSet<>(priorities).size() != priorities.size()) {
                throw new BusinessException(
                        ErrorCode.MEMBER_PRIORITY_DUPLICATED
                );
            }
        }

        String maritalStatus = preference == null
                ? null
                : preference.getMaritalStatus();
        if (request.hasMaritalStatus()) {
            maritalStatus = requirePatchValue(
                    request.getMaritalStatus(),
                    "maritalStatus"
            );
            validateProfileMaritalStatus(maritalStatus);
        }

        Boolean hasVehicle = preference == null
                ? null
                : preference.getHasVehicle();
        if (request.hasVehicle()) {
            hasVehicle = requirePatchValue(
                    request.getHasVehicle(),
                    "hasVehicle"
            );
        }

        Boolean hasChildren = preference == null
                ? null
                : preference.getHasChildren();
        if (request.hasChildren()) {
            hasChildren = requirePatchValue(
                    request.getHasChildren(),
                    "hasChildren"
            );
        }

        return new ValidatedProfileUpdate(
                nickname,
                ageGroup,
                ageGroupPublicAgreed,
                purpose,
                priorities,
                selectedCharacterId,
                maritalStatus,
                hasVehicle,
                hasChildren
        );
    }

    private <T> T requirePatchValue(T value, String field) {
        if (value == null) {
            throw invalidProfileField(
                    field,
                    "null로 수정할 수 없습니다."
            );
        }
        return value;
    }

    private String normalizeRequiredPatchText(
            String value,
            String field,
            int maxLength
    ) {
        String normalized = requirePatchValue(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalidProfileField(
                    field,
                    "1자 이상 " + maxLength + "자 이하로 입력해 주세요."
            );
        }
        return normalized;
    }

    private void validateProfileAgeGroup(String ageGroup) {
        if (!ALLOWED_AGE_GROUPS.contains(ageGroup)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_AGE_GROUP_INVALID,
                    Map.of(
                            "field", "ageGroup",
                            "allowedValues", ALLOWED_AGE_GROUPS
                    )
            );
        }
    }

    private void validateProfileCharacter(String selectedCharacterId) {
        if (!ALLOWED_CHARACTER_IDS.contains(selectedCharacterId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_CHARACTER_INVALID,
                    Map.of(
                            "field", "selectedCharacterId",
                            "allowedValues", ALLOWED_CHARACTER_IDS
                    )
            );
        }
    }

    private void validateProfileMaritalStatus(String maritalStatus) {
        if (!ALLOWED_MARITAL_STATUSES.contains(maritalStatus)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field", "maritalStatus",
                            "reason", "허용되지 않은 값입니다.",
                            "allowedValues", ALLOWED_MARITAL_STATUSES
                    )
            );
        }
    }

    private BusinessException invalidProfileField(
            String field,
            String reason
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", field,
                        "reason", reason
                )
        );
    }

    private void persistProfileUpdate(
            MemberPreference preference,
            boolean preferenceChanged
    ) {
        try {
            if (preferenceChanged) {
                memberPreferenceRepository.save(preference);
            }
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (MEMBER_NICKNAME_UNIQUE_CONSTRAINT.equals(
                    findConstraintName(exception)
            )) {
                throw new BusinessException(
                        ErrorCode.AUTH_NICKNAME_DUPLICATED
                );
            }
            throw exception;
        }
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private ValidatedOnboarding validate(OnboardingRequest request) {
        String purpose = required(request.purpose(), "purpose");
        String maritalStatus = required(
                request.maritalStatus(),
                "maritalStatus"
        );
        boolean hasVehicle = required(
                request.hasVehicle(),
                "hasVehicle"
        );
        boolean hasChildren = required(
                request.hasChildren(),
                "hasChildren"
        );
        List<String> priorities = requiredPriorities(request.priorities());
        String ageGroup = required(request.ageGroup(), "ageGroup");
        boolean ageGroupPublicAgreed = required(
                request.ageGroupPublicAgreed(),
                "ageGroupPublicAgreed"
        );

        validateAllowed(purpose, ALLOWED_PURPOSES, "purpose");
        validateAllowed(
                maritalStatus,
                ALLOWED_MARITAL_STATUSES,
                "maritalStatus"
        );
        validateAgeGroup(ageGroup);

        String selectedCharacterId = required(
                request.selectedCharacterId(),
                "selectedCharacterId"
        );
        validateCharacter(selectedCharacterId);

        List<String> normalizedPriorities = priorities.stream()
                .map(this::normalizePriority)
                .toList();
        if (new HashSet<>(normalizedPriorities).size()
                != normalizedPriorities.size()) {
            throw new BusinessException(
                    ErrorCode.MEMBER_PRIORITY_DUPLICATED
            );
        }

        return new ValidatedOnboarding(
                purpose,
                maritalStatus,
                hasVehicle,
                hasChildren,
                normalizedPriorities,
                ageGroup,
                ageGroupPublicAgreed,
                selectedCharacterId
        );
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw requiredFieldMissing(field);
        }
        return value.strip();
    }

    private boolean required(Boolean value, String field) {
        if (value == null) {
            throw requiredFieldMissing(field);
        }
        return value;
    }

    private List<String> requiredPriorities(List<String> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            throw requiredFieldMissing("priorities");
        }
        return priorities;
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            throw invalidValue("priorities");
        }
        String normalized = priority.strip();
        validateAllowed(normalized, ALLOWED_PRIORITIES, "priorities");
        return normalized;
    }

    private void validateCharacter(String selectedCharacterId) {
        if (!ALLOWED_CHARACTER_IDS.contains(selectedCharacterId)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_CHARACTER_INVALID,
                    Map.of("allowedValues", ALLOWED_CHARACTER_IDS)
            );
        }
    }

    private void validateAgeGroup(String ageGroup) {
        if (!ALLOWED_AGE_GROUPS.contains(ageGroup)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_AGE_GROUP_INVALID,
                    Map.of("allowedValues", ALLOWED_AGE_GROUPS)
            );
        }
    }

    private void validateAllowed(
            String value,
            List<String> allowedValues,
            String field
    ) {
        if (!allowedValues.contains(value)) {
            throw invalidValue(field);
        }
    }

    private BusinessException requiredFieldMissing(String field) {
        return new BusinessException(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING,
                Map.of(
                        "field", field,
                        "reason", "필수 값입니다."
                )
        );
    }

    private BusinessException invalidValue(String field) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of(
                        "field", field,
                        "reason", "허용되지 않은 값입니다."
                )
        );
    }

    private ParsedPublicReport parsePublicReportResult(
            Long reportId,
            String resultJson
    ) {
        if (resultJson == null || resultJson.isBlank()) {
            return ParsedPublicReport.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return new ParsedPublicReport(
                    reportTextOrNull(root.get("title")),
                    reportTextOrNull(root.get("summary")),
                    reportStringList(root.get("analysisTags"))
            );
        } catch (JsonProcessingException exception) {
            log.warn(
                    "공개 프로필 리포트 결과를 읽지 못했습니다. reportId={}",
                    reportId
            );
            return ParsedPublicReport.empty();
        }
    }

    private String reportTextOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.textValue();
    }

    private List<String> reportStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.textValue());
            }
        });
        return List.copyOf(values);
    }

    private void validateActiveMember(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }
    }

    private void validatePublicTarget(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private record ValidatedOnboarding(
            String purpose,
            String maritalStatus,
            boolean hasVehicle,
            boolean hasChildren,
            List<String> priorities,
            String ageGroup,
            boolean ageGroupPublicAgreed,
            String selectedCharacterId
    ) {
    }

    private record ParsedPublicReport(
            String title,
            String summary,
            List<String> analysisTags
    ) {
        private static ParsedPublicReport empty() {
            return new ParsedPublicReport(null, null, List.of());
        }
    }

    private record ValidatedProfileUpdate(
            String nickname,
            String ageGroup,
            boolean ageGroupPublicAgreed,
            String purpose,
            List<String> priorities,
            String selectedCharacterId,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren
    ) {
    }
}
