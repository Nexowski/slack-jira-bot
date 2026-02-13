package com.mlorenc.slack.jira.bot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class AppConfig {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
