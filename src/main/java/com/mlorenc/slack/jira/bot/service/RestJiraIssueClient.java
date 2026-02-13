package com.mlorenc.slack.jira.bot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
public class RestJiraIssueClient implements JiraIssueClient {

    private final RestTemplate restTemplate;

    public RestJiraIssueClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String fetchIssueProjectKey(String cloudId, String accessToken, String issueKey) {
        String url = jiraApiBase(cloudId) + "/rest/api/3/issue/" + issueKey + "?fields=project";
        ResponseEntity<JiraIssueResponse> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), JiraIssueResponse.class);
        JiraIssueResponse body = response.getBody();
        if (body == null || body.fields() == null || body.fields().project() == null || body.fields().project().key() == null) {
            throw new IllegalStateException("Could not determine Jira project key for issue " + issueKey);
        }
        return body.fields().project().key();
    }

    @Override
    public Optional<String> findFieldIdByName(String cloudId, String accessToken, String fieldName) {
        String url = jiraApiBase(cloudId) + "/rest/api/3/field";
        ResponseEntity<JiraField[]> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)), JiraField[].class);
        JiraField[] fields = response.getBody();
        if (fields == null) {
            return Optional.empty();
        }
        return Arrays.stream(fields)
                .filter(field -> field.name() != null && field.name().equalsIgnoreCase(fieldName))
                .map(JiraField::id)
                .findFirst();
    }

    @Override
    public void updateIssueField(String cloudId, String accessToken, String issueKey, String fieldId, String value) {
        String url = jiraApiBase(cloudId) + "/rest/api/3/issue/" + issueKey;
        Map<String, Object> payload = Map.of("fields", Map.of(fieldId, value));

        HttpHeaders headers = authHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(payload, headers), Void.class);
    }

    private static String jiraApiBase(String cloudId) {
        return "https://api.atlassian.com/ex/jira/" + cloudId;
    }

    private static HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraIssueResponse(JiraIssueFields fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraIssueFields(JiraProject project) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraProject(String key) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JiraField(String id, String name) {
    }
}
