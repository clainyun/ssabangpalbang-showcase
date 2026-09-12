package com.ssafy.ssabangpalbang.member.auth;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityContextLoginMemberResolverTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final SecurityContextLoginMemberResolver resolver =
            new SecurityContextLoginMemberResolver(memberRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void JWT_인증_회원과_활성_상태를_반환한다() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        authenticate(1L);

        assertThat(resolver.resolve()).isEqualTo(new LoginMember(1L, true));
    }

    @Test
    void SecurityContext에_인증_회원이_없으면_인증_오류를_반환한다() {
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void JWT_회원이_없으면_회원_없음_오류를_반환한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        authenticate(1L);

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 탈퇴_회원은_비활성_회원으로_반환한다() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        authenticate(1L);

        assertThat(resolver.resolve()).isEqualTo(new LoginMember(1L, false));
    }

    private void authenticate(Long memberId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedMember(memberId),
                        null,
                        List.of()
                )
        );
    }
}
