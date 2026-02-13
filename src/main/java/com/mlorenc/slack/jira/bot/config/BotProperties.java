package com.mlorenc.slack.jira.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public record BotProperties(Slack slack, Jira jira, Security security) {

    public record Slack(String botToken, String signingSecret) {
    }

    public record Jira(String clientId,
                       String clientSecret,
                       String redirectUri,
                       String scopes,
                       String authorizeUrl,
                       String tokenUrl,
                       String resourcesUrl) {
    }

    public record Security(String encryptionKey) {
    }
}
