package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.member.dto.request.FcmTokenRegisterRequest;
import com.ssafy.ssabangpalbang.member.dto.response.FcmTokenDeleteResponse;
import com.ssafy.ssabangpalbang.member.entity.FcmToken;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRegistrationLock;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.response.MemberResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private FcmTokenRegistrationLock fcmTokenRegistrationLock;

    @Mock
    private LoginMemberResolver loginMemberResolver;

    private FcmTokenService fcmTokenService;

    @BeforeEach
    void setUp() {
        fcmTokenService = new FcmTokenService(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 신규_토큰을_등록한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty());

        FcmTokenService.RegistrationResult result = register(
                "device-A",
                "token-A"
        );

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getToken()).isEqualTo("token-A");
        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_REGISTERED);
        assertThat(result.response().registered()).isTrue();
    }

    @Test
    void 등록_쓰기_전에_기기와_토큰을_잠근다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty());

        register("device-A", "token-A");

        InOrder inOrder = inOrder(
                fcmTokenRegistrationLock,
                fcmTokenRepository
        );
        inOrder.verify(fcmTokenRegistrationLock)
                .acquire("device-A", "token-A");
        inOrder.verify(fcmTokenRepository)
                .deleteByDeviceIdAndOtherMember("device-A", MEMBER_ID);
    }

    @Test
    void 동일_요청을_두_번_호출해도_한_행만_유지한다() {
        activeMember();
        FcmToken existing = fcmToken(11L, MEMBER_ID, "token-A", "device-A");
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        register("device-A", "token-A");
        FcmTokenService.RegistrationResult second = register("device-A", "token-A");

        verify(fcmTokenRepository, times(1)).save(any(FcmToken.class));
        assertThat(second.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_UPDATED);
    }

    @Test
    void 같은_기기의_토큰을_갱신해도_기존_행_ID를_유지한다() {
        activeMember();
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        FcmToken existing = fcmToken(11L, MEMBER_ID, "old-token", "device-A", before);
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.of(existing));

        FcmTokenService.RegistrationResult result = register(
                "device-A",
                "new-token"
        );

        assertThat(existing.getId()).isEqualTo(11L);
        assertThat(existing.getToken()).isEqualTo("new-token");
        assertThat(existing.getUpdatedAt()).isAfter(before);
        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_UPDATED);
        verify(fcmTokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    void 다른_회원이_사용하던_기기_ID_연결을_제거하고_등록한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty());

        register("device-A", "token-A");

        verify(fcmTokenRepository).deleteByDeviceIdAndOtherMember(
                "device-A",
                MEMBER_ID
        );
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void 다른_회원_기기에_연결된_동일_토큰을_이전_연결에서_제거한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty());

        register("device-A", "shared-token");

        verify(fcmTokenRepository).deleteByTokenOnOtherOwner(
                "shared-token",
                MEMBER_ID,
                "device-A"
        );
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void 같은_회원의_다른_기기에_연결된_동일_토큰도_이전_연결에서_제거한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-B"))
                .thenReturn(Optional.empty());

        register("device-B", "shared-token");

        verify(fcmTokenRepository).deleteByTokenOnOtherOwner(
                "shared-token",
                MEMBER_ID,
                "device-B"
        );
    }

    @Test
    void 한_회원이_두_기기에_서로_다른_토큰을_등록할_수_있다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        register("device-A", "token-A");
        register("device-B", "token-B");

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FcmToken::getDeviceId)
                .containsExactly("device-A", "device-B");
    }

    @Test
    void 공백뿐인_기기_ID는_거절한다() {
        assertThatThrownBy(() -> register("  ", "token-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 길이가_256자인_기기_ID는_거절한다() {
        assertThatThrownBy(() -> register("a".repeat(256), "token-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void NUL_문자가_포함된_기기_ID는_거절한다() {
        assertThatThrownBy(() -> register("device\u0000-A", "token-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 공백뿐인_FCM_토큰은_거절한다() {
        assertThatThrownBy(() -> register("device-A", "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 길이가_256자인_FCM_토큰은_거절한다() {
        assertThatThrownBy(() -> register("device-A", "a".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void NUL_문자가_포함된_FCM_토큰은_거절한다() {
        assertThatThrownBy(() -> register("device-A", "token\u0000-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 기기_ID와_FCM_토큰의_앞뒤_공백을_제거한다() {
        activeMember();
        when(fcmTokenRepository.findByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(Optional.empty());

        FcmTokenService.RegistrationResult result = register(
                "  device-A  ",
                "  token-A  "
        );

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo("device-A");
        assertThat(captor.getValue().getToken()).isEqualTo("token-A");
        assertThat(result.response().deviceId()).isEqualTo("device-A");
    }

    @Test
    void 비활성_회원은_회원_없음_오류를_반환한다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> register("device-A", "token-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 비활성_회원은_FCM_토큰_행을_생성하지_않는다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> register("device-A", "token-A"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock
        );
    }

    @Test
    void 비활성_회원_요청은_기존_토큰도_변경하지_않는다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));
        FcmToken existing = fcmToken(11L, MEMBER_ID, "old-token", "device-A");

        assertThatThrownBy(() -> register("device-A", "new-token"))
                .isInstanceOf(BusinessException.class);

        assertThat(existing.getToken()).isEqualTo("old-token");
        verifyNoInteractions(fcmTokenRepository);
    }

    @Test
    void 현재_회원과_기기의_FCM_토큰_연결을_삭제한다() {
        activeMember();
        when(fcmTokenRepository.deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(1);

        FcmTokenService.DeletionResult result = delete("device-A");

        verify(fcmTokenRepository).deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A");
        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_DELETED);
        assertThat(result.response())
                .extracting(
                        FcmTokenDeleteResponse::deviceId,
                        FcmTokenDeleteResponse::registered,
                        FcmTokenDeleteResponse::deletedAt
                )
                .containsExactly("device-A", false, result.response().deletedAt());
    }

    @Test
    void 삭제_쓰기_전에_등록과_같은_기기_잠금을_획득한다() {
        activeMember();
        when(fcmTokenRepository.deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(1);

        delete("device-A");

        InOrder inOrder = inOrder(
                fcmTokenRegistrationLock,
                fcmTokenRepository
        );
        inOrder.verify(fcmTokenRegistrationLock)
                .acquireDevice("device-A");
        inOrder.verify(fcmTokenRepository)
                .deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A");
    }

    @Test
    void 존재하지_않는_FCM_토큰_연결도_성공으로_처리한다() {
        activeMember();
        when(fcmTokenRepository.deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(0);

        FcmTokenService.DeletionResult result = delete("device-A");

        assertThat(result.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_ALREADY_DELETED);
        assertThat(result.response().registered()).isFalse();
    }

    @Test
    void FCM_토큰_삭제를_반복하면_두번째부터_이미_삭제됨을_반환한다() {
        activeMember();
        when(fcmTokenRepository.deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(1, 0);

        FcmTokenService.DeletionResult first = delete("device-A");
        FcmTokenService.DeletionResult second = delete("device-A");

        verify(fcmTokenRepository, times(2))
                .deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A");
        assertThat(first.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_DELETED);
        assertThat(second.responseCode())
                .isEqualTo(MemberResponseCode.FCM_TOKEN_ALREADY_DELETED);
        assertThat(first.response().registered()).isFalse();
        assertThat(second.response().registered()).isFalse();
    }

    @Test
    void FCM_토큰_삭제에_공백이_포함된_기기_ID는_정규화한다() {
        activeMember();
        when(fcmTokenRepository.deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A"))
                .thenReturn(1);

        FcmTokenService.DeletionResult result = delete("  device-A  ");

        verify(fcmTokenRepository).deleteByMemberIdAndDeviceId(MEMBER_ID, "device-A");
        assertThat(result.response().deviceId()).isEqualTo("device-A");
    }

    @Test
    void 공백뿐인_기기_ID로_FCM_토큰을_삭제할_수_없다() {
        assertThatThrownBy(() -> delete("  "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 길이가_256자인_기기_ID로_FCM_토큰을_삭제할_수_없다() {
        assertThatThrownBy(() -> delete("a".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_DEVICE_ID_INVALID);

        verifyNoInteractions(
                fcmTokenRepository,
                fcmTokenRegistrationLock,
                loginMemberResolver
        );
    }

    @Test
    void 비활성_회원은_FCM_토큰_연결을_삭제할_수_없다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> delete("device-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

        verifyNoInteractions(fcmTokenRepository);
    }

    private FcmTokenService.RegistrationResult register(
            String deviceId,
            String token
    ) {
        return fcmTokenService.register(
                deviceId,
                new FcmTokenRegisterRequest(token)
        );
    }

    private FcmTokenService.DeletionResult delete(String deviceId) {
        return fcmTokenService.delete(deviceId);
    }

    private void activeMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private FcmToken fcmToken(
            Long id,
            Long memberId,
            String token,
            String deviceId
    ) {
        return fcmToken(
                id,
                memberId,
                token,
                deviceId,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );
    }

    private FcmToken fcmToken(
            Long id,
            Long memberId,
            String token,
            String deviceId,
            OffsetDateTime updatedAt
    ) {
        FcmToken fcmToken = FcmToken.of(memberId, token, deviceId, updatedAt);
        ReflectionTestUtils.setField(fcmToken, "id", id);
        return fcmToken;
    }
}
