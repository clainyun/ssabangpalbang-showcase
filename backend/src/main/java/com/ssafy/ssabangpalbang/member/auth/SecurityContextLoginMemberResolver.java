package com.ssafy.ssabangpalbang.member.auth;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextLoginMemberResolver implements LoginMemberResolver {

    private final MemberRepository memberRepository;

    @Override
    public LoginMember resolve() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof AuthenticatedMember authenticatedMember)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository
                .findById(authenticatedMember.memberId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        return new LoginMember(
                member.getId(),
                member.getStatus() == MemberStatus.ACTIVE
        );
    }
}
