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
import java.util.Optional;

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

    public Optional<FieldOption> findFieldById(String slackUserId, String fieldId) {
        if (fieldId == null || fieldId.isBlank()) {
            return Optional.empty();
        }
        return fetchAllFields(slackUserId).stream()
                .filter(field -> field.id().equals(fieldId))
                .findFirst();
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
                FieldOption field = mapField(map);
                if (field != null) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static FieldOption mapField(Map<?, ?> map) {
        Object id = map.get("id");
        Object name = map.get("name");
        if (id == null || name == null) {
            return null;
        }

        String schemaType = "";
        Object schema = map.get("schema");
        if (schema instanceof Map<?, ?> schemaMap) {
            Object type = schemaMap.get("type");
            if (type != null) {
                schemaType = String.valueOf(type);
            }
        }

        List<String> allowedValues = List.of();
        Object rawAllowedValues = map.get("allowedValues");
        if (rawAllowedValues instanceof List<?> rawList) {
            allowedValues = rawList.stream()
                    .map(JiraFieldService::coerceAllowedValue)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        }

        return new FieldOption(String.valueOf(id), String.valueOf(name), schemaType, allowedValues);
    }

    private static String coerceAllowedValue(Object entry) {
        if (entry instanceof Map<?, ?> optionMap) {
            Object value = optionMap.get("value");
            if (value != null) {
                return String.valueOf(value);
            }
            Object name = optionMap.get("name");
            if (name != null) {
                return String.valueOf(name);
            }
            Object id = optionMap.get("id");
            if (id != null) {
                return String.valueOf(id);
            }
        }
        return entry == null ? null : String.valueOf(entry);
    }

    public record FieldOption(String id, String name, String type, List<String> allowedValues) {
    }
}
