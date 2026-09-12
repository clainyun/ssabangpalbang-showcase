package com.ssafy.ssabangpalbang.chatbot.config;

import com.ssafy.ssabangpalbang.chatbot.client.ChatbotAiClient;
import com.ssafy.ssabangpalbang.chatbot.client.RestChatbotAiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ChatbotAiProperties.class)
public class ChatbotAiConfig {

    @Bean
    @ConditionalOnMissingBean(ChatbotAiClient.class)
    ChatbotAiClient chatbotAiClient(
            ChatbotAiProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();

        return new RestChatbotAiClient(restClient, properties);
    }
}
