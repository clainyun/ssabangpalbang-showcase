package com.ssafy.ssabangpalbang.auth.config;

import com.ssafy.ssabangpalbang.auth.token.JwtProperties;
import com.ssafy.ssabangpalbang.auth.oauth.SocialOAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        SocialOAuthProperties.class
})
public class AuthConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
