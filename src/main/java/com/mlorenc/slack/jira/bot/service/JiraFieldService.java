package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.UserConnection;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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

    public List<FieldOption> searchFields(String slackUserId, String query) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return fetchAllFields(slackUserId).stream()
                .filter(field -> normalizedQuery.isBlank()
                        || field.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || field.id().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .limit(100)
                .toList();
    }

    public List<FieldOption> fetchAllFields(String slackUserId) {
        String accessToken = jiraOAuthService.getValidAccessToken(slackUserId);
        UserConnection connection = userConnectionRepository.findBySlackUserId(slackUserId)
                .orElseThrow(() -> new IllegalArgumentException("No Jira connection for user"));

        String endpoint = "https://api.atlassian.com/ex/jira/%s/rest/api/3/field".formatted(connection.getJiraCloudId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<List> response = restTemplate.exchange(
                endpoint,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class);

        List<?> body = response.getBody();
        if (body == null) {
            return List.of();
        }

        List<FieldOption> fields = new ArrayList<>();
        for (Object entry : body) {
            if (entry instanceof Map<?, ?> map) {
                Object id = map.get("id");
                Object name = map.get("name");
                if (id != null && name != null) {
                    fields.add(new FieldOption(String.valueOf(id), String.valueOf(name)));
                }
            }
        }
        return fields;
    }

    public record FieldOption(String id, String name) {
    }
}
