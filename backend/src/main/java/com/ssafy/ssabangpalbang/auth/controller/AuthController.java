package com.ssafy.ssabangpalbang.auth.controller;

import com.ssafy.ssabangpalbang.auth.code.AuthSuccessCode;
import com.ssafy.ssabangpalbang.auth.dto.request.LoginRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.LogoutRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetCodeRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetConfirmRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetVerifyRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SignupRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SocialLoginRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.SocialSignupRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.TokenReissueRequest;
import com.ssafy.ssabangpalbang.auth.dto.response.AvailabilityResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.LoginResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.PasswordResetVerifyResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SignupResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SocialLoginResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.SocialSignupResponse;
import com.ssafy.ssabangpalbang.auth.dto.response.TokenReissueResponse;
import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.auth.service.AuthService;
import com.ssafy.ssabangpalbang.auth.service.PasswordResetService;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/password-reset/request")
    @Operation(
            summary = "비밀번호 재설정 코드 발송 요청",
            description = "이메일로 6자리 재설정 코드를 발송한다. 계정 존재 여부를 노출하지 않기 위해"
                    + " 미가입·소셜 전용·탈퇴 계정 여부와 무관하게 항상 200과 동일한 메시지를 반환한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재설정 코드 발송 요청 접수(존재 여부와 무관하게 동일 응답)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류 등 입력값 오류"
            )
    })
    public ApiResponse<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetCodeRequest request
    ) {
        passwordResetService.requestPasswordReset(request);
        return ApiResponse.success(AuthSuccessCode.PASSWORD_RESET_REQUESTED);
    }

    @PostMapping("/password-reset/confirm")
    @Operation(
            summary = "비밀번호 재설정 코드 검증 및 새 비밀번호 설정",
            description = "이메일로 받은 재설정 코드를 검증하고 새 비밀번호로 갱신한다."
                    + " 성공 시 기존 Refresh Token을 모두 무효화한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "비밀번호 재설정 완료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "코드 불일치·만료 또는 비밀번호 정책 위반"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 처리된 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "비밀번호가 없는 소셜 전용 계정"
            )
    })
    public ApiResponse<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirmPasswordReset(request);
        return ApiResponse.success(AuthSuccessCode.PASSWORD_RESET_SUCCESS);
    }

    @PostMapping("/password-reset/verify")
    @Operation(
            summary = "비밀번호 재설정 코드 검증",
            description = "이메일로 받은 재설정 코드가 유효한지만 확인한다. 비밀번호는 변경하지 않고"
                    + " 코드도 소비하지 않는다. 무차별 대입 방어를 위해 confirm과 동일한 시도 횟수"
                    + " 제한을 공유하며, 임계치를 초과하면 코드를 폐기하고 400을 반환한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "코드 검증 결과(valid=true/false)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 시도 횟수 초과로 코드 폐기"
            )
    })
    public ApiResponse<PasswordResetVerifyResponse> verifyPasswordResetCode(
            @Valid @RequestBody PasswordResetVerifyRequest request
    ) {
        boolean valid = passwordResetService.verifyCode(request);
        return ApiResponse.success(
                AuthSuccessCode.PASSWORD_RESET_CODE_VERIFY_SUCCESS,
                PasswordResetVerifyResponse.of(valid)
        );
    }

    @GetMapping("/check-email")
    @Operation(
            summary = "이메일 중복 확인",
            description = "회원가입 전 이메일 사용 가능 여부를 확인한다."
                    + " available=true이면 아직 사용되지 않아 가입에 사용할 수 있다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이메일 사용 가능 여부(available=true/false)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류 등 입력값 오류"
            )
    })
    public ApiResponse<AvailabilityResponse> checkEmail(
            @Parameter(description = "확인할 이메일", required = true)
            @RequestParam("email")
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "올바른 이메일 형식이 아닙니다.")
            @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
            String email
    ) {
        return ApiResponse.success(
                AuthSuccessCode.EMAIL_AVAILABILITY_SUCCESS,
                authService.checkEmailAvailability(email)
        );
    }

    @GetMapping("/check-nickname")
    @Operation(
            summary = "닉네임 중복 확인",
            description = "회원가입 전 닉네임 사용 가능 여부를 확인한다."
                    + " available=true이면 아직 사용되지 않아 가입에 사용할 수 있다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "닉네임 사용 가능 여부(available=true/false)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "닉네임 형식 오류 등 입력값 오류"
            )
    })
    public ApiResponse<AvailabilityResponse> checkNickname(
            @Parameter(description = "확인할 닉네임", required = true)
            @RequestParam("nickname")
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
            String nickname
    ) {
        return ApiResponse.success(
                AuthSuccessCode.NICKNAME_AVAILABILITY_SUCCESS,
                authService.checkNicknameAvailability(nickname)
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "로그아웃",
            description = "인증된 회원 본인의 Refresh Token을 무효화한다."
                    + " Access Token 인증이 필요하다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Refresh Token 누락 또는 본인 소유가 아닌 Refresh Token"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token 인증 실패 또는 Refresh Token이"
                            + " 유효하지 않거나 만료됨"
            )
    })
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(authenticatedMember.memberId(), request);
        return ApiResponse.success(AuthSuccessCode.LOGOUT_SUCCESS);
    }

    @PostMapping("/reissue")
    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 검증해 새 Access/Refresh Token을 발급하고"
                    + " 기존 Refresh Token은 회전 방식으로 무효화한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Refresh Token 누락 등 입력값 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token이 유효하지 않음, 만료됨 또는 이미 폐기됨"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 처리된 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    public ApiResponse<TokenReissueResponse> reissue(
            @Valid @RequestBody TokenReissueRequest request
    ) {
        return ApiResponse.success(
                AuthSuccessCode.TOKEN_REISSUE_SUCCESS,
                authService.reissue(request)
        );
    }

    @PostMapping("/login")
    @Operation(
            summary = "이메일 로그인",
            description = "이메일과 비밀번호를 검증하고 Access/Refresh Token을 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류 또는 필수값 누락"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴 처리된 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "비밀번호가 없는 소셜 전용 계정"
            )
    })
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ApiResponse.success(
                AuthSuccessCode.LOGIN_SUCCESS,
                authService.login(request)
        );
    }

    @PostMapping("/social-login")
    @Operation(
            summary = "네이버/카카오 간편 로그인",
            description = "소셜 인가 코드를 검증하고 기존 회원에게는 서비스 토큰을, 신규 사용자에게는 10분짜리 회원가입 임시 토큰을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 또는 신규 소셜 회원가입 필요"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 제공자 또는 조건부 필수값 누락"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "소셜 인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "소셜 제공자 장애 또는 설정 누락"
            )
    })
    public ApiResponse<SocialLoginResponse> socialLogin(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        AuthService.SocialLoginResult result = authService.socialLogin(request);

        return ApiResponse.success(
                result.responseCode(),
                result.response()
        );
    }

    @PostMapping("/social-signup")
    @Operation(
            summary = "소셜 회원가입 완료",
            description = "소셜 로그인에서 발급한 10분짜리 임시 토큰으로 회원과 소셜 계정을 생성하고 서비스 토큰을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "소셜 회원가입 완료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "임시 토큰 오류, 이메일 필요 또는 입력값 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "소셜 회원가입 임시 토큰 만료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이메일, 닉네임 또는 소셜 계정 중복"
            )
    })
    public ResponseEntity<ApiResponse<SocialSignupResponse>> socialSignup(
            @Valid @RequestBody SocialSignupRequest request
    ) {
        SocialSignupResponse response = authService.socialSignup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        AuthSuccessCode.SOCIAL_SIGNUP_SUCCESS,
                        response
                ));
    }

    @PostMapping("/signup")
    @Operation(
            summary = "이메일 회원가입",
            description = "이메일·비밀번호·닉네임으로 회원을 생성하고"
                    + " Access/Refresh Token을 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "회원가입 완료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식, 비밀번호 정책(8자 이상 영문+숫자),"
                            + " 닉네임 길이 등 입력값 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이메일 또는 닉네임 중복"
            )
    })
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        AuthSuccessCode.SIGNUP_SUCCESS,
                        response
                ));
    }
}
