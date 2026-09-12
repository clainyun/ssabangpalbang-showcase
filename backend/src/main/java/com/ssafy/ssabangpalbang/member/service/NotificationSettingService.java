package com.ssafy.ssabangpalbang.member.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingUpdateResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final LoginMemberResolver loginMemberResolver;

    public NotificationSettingResponse getNotificationSettings() {
        LoginMember loginMember = loginMemberResolver.resolve();

        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        Member member = memberRepository
                .findById(loginMember.memberId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        return new NotificationSettingResponse(
                member.isServiceNotificationAgreed(),
                member.isAdNotificationAgreed()
        );
    }

    @Transactional
    public NotificationSettingUpdateResponse update(JsonNode requestBody) {
        if (requestBody == null
                || (!requestBody.has("serviceNotificationAgreed")
                && !requestBody.has("adNotificationAgreed"))) {
            throw new BusinessException(
                    ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY
            );
        }

        Boolean serviceNotificationAgreed = notificationAgreement(
                requestBody,
                "serviceNotificationAgreed"
        );
        Boolean adNotificationAgreed = notificationAgreement(
                requestBody,
                "adNotificationAgreed"
        );

        LoginMember loginMember = loginMemberResolver.resolve();
        if (!loginMember.active()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        Member member = memberRepository
                .findById(loginMember.memberId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        member.updateNotificationSettings(
                serviceNotificationAgreed,
                adNotificationAgreed
        );
        memberRepository.flush();

        return new NotificationSettingUpdateResponse(
                member.isServiceNotificationAgreed(),
                member.isAdNotificationAgreed(),
                OffsetDateTime.ofInstant(member.getUpdatedAt(), SEOUL_ZONE_ID)
        );
    }

    private Boolean notificationAgreement(
            JsonNode requestBody,
            String field
    ) {
        if (!requestBody.has(field)) {
            return null;
        }

        JsonNode value = requestBody.get(field);
        if (value == null || value.isNull()) {
            throw invalidNotificationAgreement(
                    field,
                    "값을 비워 둘 수 없습니다."
            );
        }
        if (!value.isBoolean()) {
            throw invalidNotificationAgreement(
                    field,
                    "true 또는 false 값을 입력해 주세요."
            );
        }

        return value.booleanValue();
    }

    private BusinessException invalidNotificationAgreement(
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
}
