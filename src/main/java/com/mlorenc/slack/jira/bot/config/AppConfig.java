package com.mlorenc.slack.jira.bot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableConfigurationProperties(BotProperties.class)
public class AppConfig {

    @Bean
    RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);
        return new RestTemplate(requestFactory);
    }


    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("jiraFields");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1_000));
        return manager;
    }
}
