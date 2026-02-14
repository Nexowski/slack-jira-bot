package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.model.JiraOAuthToken;
import com.mlorenc.slack.jira.bot.model.OAuthState;
import com.mlorenc.slack.jira.bot.model.UserConnection;
import com.mlorenc.slack.jira.bot.repository.JiraOAuthTokenRepository;
import com.mlorenc.slack.jira.bot.repository.OAuthStateRepository;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JiraOAuthService {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthService.class);

    private final BotProperties properties;
    private final OAuthStateRepository stateRepository;
    private final JiraOAuthTokenRepository tokenRepository;
    private final UserConnectionRepository userConnectionRepository;
    private final TokenEncryptionService encryptionService;
    private final RestTemplate restTemplate;

    public JiraOAuthService(BotProperties properties,
                            OAuthStateRepository stateRepository,
                            JiraOAuthTokenRepository tokenRepository,
                            UserConnectionRepository userConnectionRepository,
                            TokenEncryptionService encryptionService,
                            RestTemplate restTemplate) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.tokenRepository = tokenRepository;
        this.userConnectionRepository = userConnectionRepository;
        this.encryptionService = encryptionService;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public String createAuthorizationUrl(String slackUserId) {
        OAuthState state = new OAuthState();
        state.setState(UUID.randomUUID().toString());
        state.setSlackUserId(slackUserId);
        state.setExpiresAt(Instant.now().plusSeconds(600));
        stateRepository.save(state);

        String url = "%s?audience=api.atlassian.com&client_id=%s&scope=%s&redirect_uri=%s&response_type=code&prompt=consent&state=%s"
                .formatted(properties.jira().authorizeUrl(),
                        encode(properties.jira().clientId()),
                        encode(properties.jira().scopes()),
                        encode(properties.jira().redirectUri()),
                        encode(state.getState()));
        log.atInfo().addKeyValue("event", "jira.oauth.authorize.created").addKeyValue("slackUserId", slackUserId).log("Created Jira OAuth authorization URL");
        return url;
    }

    @Transactional
    public CallbackResult handleCallback(String code, String stateValue) {
        OAuthState state = stateRepository.findById(stateValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OAuth state"));
        if (state.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth state expired");
        }

        String priorCloudId = userConnectionRepository.findBySlackUserId(state.getSlackUserId())
                .map(UserConnection::getJiraCloudId)
                .orElse(null);

        TokenResponse tokenResponse = exchangeCode(code);
        saveOrUpdateToken(state.getSlackUserId(), tokenResponse);

        CloudResource resource = fetchCloudResource(tokenResponse.accessToken());
        UserConnection connection = userConnectionRepository.findBySlackUserId(state.getSlackUserId())
                .orElseGet(UserConnection::new);
        connection.setSlackUserId(state.getSlackUserId());
        connection.setJiraAccountId("oauth-user");
        connection.setJiraCloudId(resource.id());
        connection.setConnectedAt(Instant.now());
        userConnectionRepository.save(connection);

        stateRepository.delete(state);
        boolean reconnect = priorCloudId != null;

        log.atInfo().addKeyValue("event", reconnect ? "jira.oauth.reconnected" : "jira.oauth.connected")
                .addKeyValue("slackUserId", state.getSlackUserId())
                .addKeyValue("jiraCloudId", resource.id())
                .log(reconnect ? "Reconnected Slack user to Jira" : "Connected Slack user to Jira");

        return new CallbackResult(state.getSlackUserId(), resource.id(), reconnect);
    }


    @Transactional
    public Optional<String> findConnectedJiraCloudId(String slackUserId) {
        return userConnectionRepository.findBySlackUserId(slackUserId)
                .map(UserConnection::getJiraCloudId)
                .filter(cloudId -> {
                    try {
                        getValidAccessToken(slackUserId);
                        return true;
                    } catch (RuntimeException ex) {
                        log.atInfo().addKeyValue("event", "jira.oauth.connection.invalid")
                                .addKeyValue("slackUserId", slackUserId)
                                .addKeyValue("jiraCloudId", cloudId)
                                .log("Existing Jira connection is no longer valid", ex);
                        return false;
                    }
                });
    }

    @Transactional
    public String getValidAccessToken(String slackUserId) {
        JiraOAuthToken token = tokenRepository.findBySlackUserId(slackUserId)
                .orElseThrow(() -> new IllegalArgumentException("No Jira OAuth token for user"));

        if (token.getExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return encryptionService.decrypt(token.getEncryptedAccessToken());
        }

        String refreshToken = encryptionService.decrypt(token.getEncryptedRefreshToken());
        TokenResponse refreshed = refreshToken(refreshToken);
        saveOrUpdateToken(slackUserId, refreshed);
        log.atInfo().addKeyValue("event", "jira.oauth.token.refreshed").addKeyValue("slackUserId", slackUserId).log("Refreshed Jira OAuth access token");
        return refreshed.accessToken();
    }


    @Transactional(readOnly = true)
    public boolean isConnectedAndTokenValid(String slackUserId) {
        if (userConnectionRepository.findBySlackUserId(slackUserId).isEmpty()) {
            return false;
        }
        try {
            String token = getValidAccessToken(slackUserId);
            return token != null && !token.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private void saveOrUpdateToken(String slackUserId, TokenResponse tokenResponse) {
        JiraOAuthToken token = tokenRepository.findBySlackUserId(slackUserId)
                .orElseGet(JiraOAuthToken::new);
        token.setSlackUserId(slackUserId);
        token.setEncryptedAccessToken(encryptionService.encrypt(tokenResponse.accessToken()));
        token.setEncryptedRefreshToken(encryptionService.encrypt(tokenResponse.refreshToken()));
        token.setExpiresAt(Instant.now().plusSeconds(tokenResponse.expiresIn()));
        token.setUpdatedAt(Instant.now());
        tokenRepository.save(token);
    }

    private TokenResponse exchangeCode(String code) {
        Map<String, Object> payload = Map.of(
                "grant_type", "authorization_code",
                "client_id", properties.jira().clientId(),
                "client_secret", properties.jira().clientSecret(),
                "code", code,
                "redirect_uri", properties.jira().redirectUri());
        return fetchToken(payload);
    }

    private TokenResponse refreshToken(String refreshToken) {
        Map<String, Object> payload = Map.of(
                "grant_type", "refresh_token",
                "client_id", properties.jira().clientId(),
                "client_secret", properties.jira().clientSecret(),
                "refresh_token", refreshToken);
        return fetchToken(payload);
    }

    private TokenResponse fetchToken(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(properties.jira().tokenUrl(), HttpMethod.POST,
                new HttpEntity<>(payload, headers), Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token") || !body.containsKey("refresh_token")) {
            throw new IllegalStateException("Invalid token response");
        }

        return new TokenResponse(
                String.valueOf(body.get("access_token")),
                String.valueOf(body.get("refresh_token")),
                ((Number) body.getOrDefault("expires_in", 3600)).longValue()
        );
    }

    private CloudResource fetchCloudResource(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<List> response = restTemplate.exchange(properties.jira().resourcesUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), List.class);

        List<?> body = response.getBody();
        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("No accessible Jira resources");
        }

        Map<?, ?> first = (Map<?, ?>) body.getFirst();
        return new CloudResource(String.valueOf(first.get("id")));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record CallbackResult(String slackUserId, String jiraCloudId, boolean reconnect) {}
    record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
    record CloudResource(String id) {}
}
