package com.ssafy.ssabangpalbang.member.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "내 프로필 수정 요청. 요청에 포함된 필드만 수정합니다.")
public final class MemberProfileUpdateRequest {

    private String nickname;
    private String ageGroup;
    private Boolean ageGroupPublicAgreed;
    @Size(max = 50)
    private String purpose;
    @Size(max = 4)
    private List<@Size(max = 20) String> priorities;
    private String selectedCharacterId;
    private String maritalStatus;
    private Boolean hasVehicle;
    private Boolean hasChildren;

    private boolean nicknameProvided;
    private boolean ageGroupProvided;
    private boolean ageGroupPublicAgreedProvided;
    private boolean purposeProvided;
    private boolean prioritiesProvided;
    private boolean selectedCharacterIdProvided;
    private boolean maritalStatusProvided;
    private boolean hasVehicleProvided;
    private boolean hasChildrenProvided;

    @Schema(example = "성동구탐방러", maxLength = 50)
    public String getNickname() {
        return nickname;
    }

    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nicknameProvided = true;
        this.nickname = nickname;
    }

    @Schema(
            example = "FIFTIES",
            allowableValues = {
                    "TEENS",
                    "TWENTIES",
                    "THIRTIES",
                    "FORTIES",
                    "FIFTIES",
                    "SIXTIES_PLUS"
            }
    )
    public String getAgeGroup() {
        return ageGroup;
    }

    @JsonSetter("ageGroup")
    public void setAgeGroup(String ageGroup) {
        this.ageGroupProvided = true;
        this.ageGroup = ageGroup;
    }

    @Schema(example = "true")
    public Boolean getAgeGroupPublicAgreed() {
        return ageGroupPublicAgreed;
    }

    @JsonSetter("ageGroupPublicAgreed")
    public void setAgeGroupPublicAgreed(Boolean ageGroupPublicAgreed) {
        this.ageGroupPublicAgreedProvided = true;
        this.ageGroupPublicAgreed = ageGroupPublicAgreed;
    }

    @Schema(
            example = "RESIDENCE",
            allowableValues = {"RESIDENCE", "INVESTMENT", "STUDY"}
    )
    public String getPurpose() {
        return purpose;
    }

    @JsonSetter("purpose")
    public void setPurpose(String purpose) {
        this.purposeProvided = true;
        this.purpose = purpose;
    }

    @ArraySchema(
            maxItems = 4,
            schema = @Schema(
                    allowableValues = {
                            "TRANSPORT",
                            "SAFETY",
                            "EDUCATION",
                            "COMMERCIAL",
                            "WALKABILITY",
                            "GREEN_SPACE",
                            "PARKING",
                            "NOISE"
                    }
            )
    )
    public List<String> getPriorities() {
        return priorities;
    }

    @JsonSetter("priorities")
    public void setPriorities(List<String> priorities) {
        this.prioritiesProvided = true;
        this.priorities = priorities;
    }

    @Schema(
            example = "PALBANG_DOG",
            allowableValues = {
                    "PALBANG",
                    "PALBANG_RABBIT",
                    "PALBANG_DOG"
            }
    )
    public String getSelectedCharacterId() {
        return selectedCharacterId;
    }

    @JsonSetter("selectedCharacterId")
    public void setSelectedCharacterId(String selectedCharacterId) {
        this.selectedCharacterIdProvided = true;
        this.selectedCharacterId = selectedCharacterId;
    }

    @Schema(
            example = "MARRIED",
            allowableValues = {"SINGLE", "MARRIED"}
    )
    public String getMaritalStatus() {
        return maritalStatus;
    }

    @JsonSetter("maritalStatus")
    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatusProvided = true;
        this.maritalStatus = maritalStatus;
    }

    @Schema(example = "true")
    public Boolean getHasVehicle() {
        return hasVehicle;
    }

    @JsonSetter("hasVehicle")
    public void setHasVehicle(Boolean hasVehicle) {
        this.hasVehicleProvided = true;
        this.hasVehicle = hasVehicle;
    }

    @Schema(example = "false")
    public Boolean getHasChildren() {
        return hasChildren;
    }

    @JsonSetter("hasChildren")
    public void setHasChildren(Boolean hasChildren) {
        this.hasChildrenProvided = true;
        this.hasChildren = hasChildren;
    }

    @JsonIgnore
    public boolean hasNickname() {
        return nicknameProvided;
    }

    @JsonIgnore
    public boolean hasAgeGroup() {
        return ageGroupProvided;
    }

    @JsonIgnore
    public boolean hasAgeGroupPublicAgreed() {
        return ageGroupPublicAgreedProvided;
    }

    @JsonIgnore
    public boolean hasPurpose() {
        return purposeProvided;
    }

    @JsonIgnore
    public boolean hasPriorities() {
        return prioritiesProvided;
    }

    @JsonIgnore
    public boolean hasSelectedCharacterId() {
        return selectedCharacterIdProvided;
    }

    @JsonIgnore
    public boolean hasMaritalStatus() {
        return maritalStatusProvided;
    }

    @JsonIgnore
    public boolean hasVehicle() {
        return hasVehicleProvided;
    }

    @JsonIgnore
    public boolean hasChildren() {
        return hasChildrenProvided;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return nicknameProvided
                || ageGroupProvided
                || ageGroupPublicAgreedProvided
                || purposeProvided
                || prioritiesProvided
                || selectedCharacterIdProvided
                || maritalStatusProvided
                || hasVehicleProvided
                || hasChildrenProvided;
    }

    @JsonIgnore
    public boolean hasPreferenceField() {
        return purposeProvided
                || prioritiesProvided
                || maritalStatusProvided
                || hasVehicleProvided
                || hasChildrenProvided;
    }
}
