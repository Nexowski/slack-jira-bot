package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.model.JiraOAuthToken;
import com.mlorenc.slack.jira.bot.repository.JiraOAuthTokenRepository;
import com.mlorenc.slack.jira.bot.repository.OAuthStateRepository;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JiraOAuthServiceTest {

    private JiraOAuthTokenRepository tokenRepository;
    private JiraOAuthService service;

    @BeforeEach
    void setUp() {
        BotProperties properties = new BotProperties(
                new BotProperties.Slack("token", "secret"),
                new BotProperties.Jira("client", "secret", "http://callback", "offline_access", "https://auth", "https://token", "https://resources"),
                new BotProperties.Security("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));

        tokenRepository = mock(JiraOAuthTokenRepository.class);
        OAuthStateRepository stateRepository = mock(OAuthStateRepository.class);
        UserConnectionRepository userConnectionRepository = mock(UserConnectionRepository.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        TokenEncryptionService encryptionService = new TokenEncryptionService(properties);

        service = new JiraOAuthService(properties, stateRepository, tokenRepository, userConnectionRepository, encryptionService, restTemplate);

        JiraOAuthToken existing = new JiraOAuthToken();
        existing.setSlackUserId("U1");
        existing.setEncryptedAccessToken(encryptionService.encrypt("expired-access"));
        existing.setEncryptedRefreshToken(encryptionService.encrypt("refresh-token"));
        existing.setExpiresAt(Instant.now().minusSeconds(5));

        when(tokenRepository.findBySlackUserId("U1")).thenReturn(Optional.of(existing));
        when(restTemplate.exchange(eq("https://token"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "new-access", "refresh_token", "new-refresh", "expires_in", 1800)));
    }

    @Test
    void shouldRefreshExpiredToken() {
        String accessToken = service.getValidAccessToken("U1");

        assertThat(accessToken).isEqualTo("new-access");
        ArgumentCaptor<JiraOAuthToken> captor = ArgumentCaptor.forClass(JiraOAuthToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedAccessToken()).isNotBlank();
    }
}
