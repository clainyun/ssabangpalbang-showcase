package com.ssafy.ssabangpalbang.member.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingResponse;
import com.ssafy.ssabangpalbang.member.dto.response.NotificationSettingUpdateResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant UPDATED_AT = Instant.parse(
            "2026-07-22T01:30:00Z"
    );
    private static final OffsetDateTime SEOUL_UPDATED_AT =
            OffsetDateTime.ofInstant(UPDATED_AT, ZoneId.of("Asia/Seoul"));

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoginMemberResolver loginMemberResolver;

    private NotificationSettingService notificationSettingService;

    @BeforeEach
    void setUp() {
        notificationSettingService = new NotificationSettingService(
                memberRepository,
                loginMemberResolver
        );
    }

    @ParameterizedTest
    @MethodSource("notificationAgreements")
    void 모든_알림_동의_조합을_저장된_값_그대로_반환한다(
            boolean serviceNotificationAgreed,
            boolean adNotificationAgreed
    ) {
        Member member = member(
                serviceNotificationAgreed,
                adNotificationAgreed
        );
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationSettingResponse response = notificationSettingService
                .getNotificationSettings();

        assertThat(response).isEqualTo(new NotificationSettingResponse(
                serviceNotificationAgreed,
                adNotificationAgreed
        ));
    }

    @Test
    void 조회는_회원의_알림_동의_값을_변경하지_않는다() {
        Member member = member(true, false);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        notificationSettingService.getNotificationSettings();

        assertThat(member.isServiceNotificationAgreed()).isTrue();
        assertThat(member.isAdNotificationAgreed()).isFalse();
        verify(memberRepository, never()).save(member);
    }

    @Test
    void 동일한_회원을_연속_조회해도_같은_설정값을_반환한다() {
        Member member = member(false, true);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationSettingResponse first = notificationSettingService
                .getNotificationSettings();
        NotificationSettingResponse second = notificationSettingService
                .getNotificationSettings();

        assertThat(first).isEqualTo(second);
        verify(memberRepository, times(2)).findById(MEMBER_ID);
    }

    @Test
    void 비활성_회원이면_회원_정보_없음_예외를_반환한다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(notificationSettingService::getNotificationSettings)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(memberRepository);
    }

    @Test
    void 로그인_확인_후_회원을_찾지_못하면_회원_정보_없음_예외를_반환한다() {
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(notificationSettingService::getNotificationSettings)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void resolver_확인_후_회원이_비활성화되면_회원_정보_없음_예외를_반환한다() {
        Member member = member(true, false);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(notificationSettingService::getNotificationSettings)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 서비스_알림만_변경하고_나머지_설정과_수정_시각을_반환한다() {
        Member member = member(true, true);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationSettingUpdateResponse response = notificationSettingService
                .update(request().put("serviceNotificationAgreed", false));

        assertThat(response).isEqualTo(new NotificationSettingUpdateResponse(
                false,
                true,
                SEOUL_UPDATED_AT
        ));
        verify(memberRepository).flush();
    }

    @Test
    void 광고성_알림만_변경하고_서비스_알림은_유지한다() {
        Member member = member(false, false);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationSettingUpdateResponse response = notificationSettingService
                .update(request().put("adNotificationAgreed", true));

        assertThat(response).isEqualTo(new NotificationSettingUpdateResponse(
                false,
                true,
                SEOUL_UPDATED_AT
        ));
    }

    @Test
    void 두_알림_설정을_독립적으로_함께_변경한다() {
        Member member = member(true, false);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        notificationSettingService.update(request()
                .put("serviceNotificationAgreed", false)
                .put("adNotificationAgreed", true));

        assertThat(member.isServiceNotificationAgreed()).isFalse();
        assertThat(member.isAdNotificationAgreed()).isTrue();
    }

    @Test
    void 기존과_같은_값을_연속_요청해도_정상_처리한다() {
        Member member = member(true, false);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        NotificationSettingUpdateResponse first = notificationSettingService
                .update(request().put("serviceNotificationAgreed", true));
        NotificationSettingUpdateResponse second = notificationSettingService
                .update(request().put("serviceNotificationAgreed", true));

        assertThat(first).isEqualTo(second);
        verify(memberRepository, times(2)).flush();
    }

    @Test
    void 본문이_없거나_변경_가능한_필드가_없으면_변경_항목_없음_예외를_반환한다() {
        assertThatThrownBy(() -> notificationSettingService.update(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY);

        assertThatThrownBy(() -> notificationSettingService.update(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY);

        assertThatThrownBy(() -> notificationSettingService.update(request()
                .put("other", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY);

        verifyNoInteractions(memberRepository, loginMemberResolver);
    }

    @Test
    void null_알림_동의값은_필드와_이유를_포함한_입력값_오류를_반환한다() {
        BusinessException exception = (BusinessException) catchThrowable(
                () -> notificationSettingService.update(request().putNull(
                        "serviceNotificationAgreed"
                ))
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData()).containsEntry(
                "field",
                "serviceNotificationAgreed"
        ).containsEntry("reason", "값을 비워 둘 수 없습니다.");
        verifyNoInteractions(memberRepository, loginMemberResolver);
    }

    @Test
    void Boolean이_아닌_알림_동의값은_필드와_이유를_포함한_입력값_오류를_반환한다() {
        BusinessException exception = (BusinessException) catchThrowable(
                () -> notificationSettingService.update(request().put(
                        "adNotificationAgreed",
                        "true"
                ))
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData()).containsEntry(
                "field",
                "adNotificationAgreed"
        ).containsEntry("reason", "true 또는 false 값을 입력해 주세요.");
        verifyNoInteractions(memberRepository, loginMemberResolver);
    }

    @Test
    void 비활성_회원은_설정을_변경하지_않고_회원_정보_없음_예외를_반환한다() {
        Member member = member(true, false);
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> notificationSettingService.update(request()
                .put("serviceNotificationAgreed", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        assertThat(member.isServiceNotificationAgreed()).isTrue();
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 재조회한_회원을_찾지_못하면_설정을_변경하지_않고_회원_정보_없음_예외를_반환한다() {
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationSettingService.update(request()
                .put("serviceNotificationAgreed", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verify(memberRepository, never()).flush();
    }

    @Test
    void 재조회한_회원이_비활성_상태면_설정을_변경하지_않고_회원_정보_없음_예외를_반환한다() {
        Member member = member(true, false);
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        activeMember();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> notificationSettingService.update(request()
                .put("serviceNotificationAgreed", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        assertThat(member.isServiceNotificationAgreed()).isTrue();
        verify(memberRepository, never()).flush();
    }

    private static Stream<Arguments> notificationAgreements() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
        );
    }

    private void activeMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private Member member(
            boolean serviceNotificationAgreed,
            boolean adNotificationAgreed
    ) {
        Member member = new Member(
                "member@example.com",
                "encoded-password",
                "member"
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(
                member,
                "serviceNotificationAgreed",
                serviceNotificationAgreed
        );
        ReflectionTestUtils.setField(
                member,
                "adNotificationAgreed",
                adNotificationAgreed
        );
        ReflectionTestUtils.setField(member, "updatedAt", UPDATED_AT);
        return member;
    }

    private ObjectNode request() {
        return JsonNodeFactory.instance.objectNode();
    }
}
