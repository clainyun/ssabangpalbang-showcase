package com.ssafy.ssabangpalbang.auth.oauth;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;

public interface SocialOAuthClient {

    SocialProvider provider();

    SocialOAuthUser authenticate(SocialOAuthAuthorization authorization);
}
