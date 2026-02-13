package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.UserConnection;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JiraFieldService {

    private final JiraOAuthService jiraOAuthService;
    private final UserConnectionRepository userConnectionRepository;
    private final RestTemplate restTemplate;

    public JiraFieldService(JiraOAuthService jiraOAuthService,
                            UserConnectionRepository userConnectionRepository,
                            RestTemplate restTemplate) {
        this.jiraOAuthService = jiraOAuthService;
        this.userConnectionRepository = userConnectionRepository;
        this.restTemplate = restTemplate;
    }

    public List<JiraFieldOption> searchFields(String slackUserId, String query) {
        String queryText = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return fetchAllFields(slackUserId).stream()
                .filter(field -> field.id().startsWith("customfield_"))
                .filter(field -> queryText.isEmpty()
                        || field.id().toLowerCase(Locale.ROOT).contains(queryText)
                        || field.name().toLowerCase(Locale.ROOT).contains(queryText))
                .sorted(Comparator.comparing(JiraFieldOption::name, String.CASE_INSENSITIVE_ORDER))
                .limit(100)
                .toList();
    }

    @Cacheable(cacheNames = "jiraFields", key = "#slackUserId")
    public List<JiraFieldOption> fetchAllFields(String slackUserId) {
        UserConnection connection = userConnectionRepository.findBySlackUserId(slackUserId)
                .orElseThrow(() -> new IllegalArgumentException("Jira account not connected for user"));

        String accessToken = jiraOAuthService.getValidAccessToken(slackUserId);
        String url = "https://api.atlassian.com/ex/jira/%s/rest/api/3/field".formatted(connection.getJiraCloudId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<List> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);

        List<?> body = response.getBody();
        if (body == null) {
            return List.of();
        }

        List<JiraFieldOption> fields = new ArrayList<>();
        for (Object item : body) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object rawId = map.get("id");
            String id = rawId == null ? "" : String.valueOf(rawId);
            Object rawName = map.get("name");
            String name = rawName == null ? id : String.valueOf(rawName);
            if (!id.isBlank()) {
                fields.add(new JiraFieldOption(id, name));
            }
        }
        return fields;
    }

    public record JiraFieldOption(String id, String name) {}
}
