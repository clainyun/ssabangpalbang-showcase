package com.ssafy.ssabangpalbang.auth.oauth;

import com.ssafy.ssabangpalbang.auth.domain.SocialProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialOAuthClientRegistry {

    private final Map<SocialProvider, SocialOAuthClient> clients;

    public SocialOAuthClientRegistry(List<SocialOAuthClient> clients) {
        EnumMap<SocialProvider, SocialOAuthClient> clientMap =
                new EnumMap<>(SocialProvider.class);
        for (SocialOAuthClient client : clients) {
            SocialOAuthClient previous = clientMap.put(
                    client.provider(),
                    client
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "소셜 OAuth 클라이언트가 중복 등록되었습니다: "
                                + client.provider()
                );
            }
        }
        this.clients = Map.copyOf(clientMap);
    }

    public SocialOAuthUser authenticate(
            SocialProvider provider,
            SocialOAuthAuthorization authorization
    ) {
        SocialOAuthClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "소셜 OAuth 클라이언트가 없습니다: " + provider
            );
        }
        return client.authenticate(authorization);
    }
}
