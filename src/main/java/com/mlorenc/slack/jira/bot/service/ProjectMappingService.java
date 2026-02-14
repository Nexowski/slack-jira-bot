package com.mlorenc.slack.jira.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlorenc.slack.jira.bot.model.ProjectFieldMapping;
import com.mlorenc.slack.jira.bot.repository.ProjectFieldMappingRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectMappingService {

    private final ProjectFieldMappingRepository repository;
    private final ObjectMapper objectMapper;

    public ProjectMappingService(ProjectFieldMappingRepository repository,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void saveMapping(String slackUserId,
                            String projectKey,
                            String progressFieldId,
                            String progressFieldType,
                            List<String> progressFieldAllowedValues) {
        ProjectFieldMapping mapping = repository.findBySlackUserIdAndJiraProjectKey(slackUserId, projectKey)
                .orElseGet(ProjectFieldMapping::new);
        mapping.setSlackUserId(slackUserId);
        mapping.setJiraProjectKey(projectKey.toUpperCase());
        mapping.setProgressFieldId(progressFieldId);
        mapping.setProgressFieldType(progressFieldType == null ? "" : progressFieldType);
        mapping.setProgressFieldAllowedValues(toJson(progressFieldAllowedValues));
        repository.save(mapping);
    }

    public Optional<String> getProgressField(String slackUserId, String projectKey) {
        return repository.findBySlackUserIdAndJiraProjectKey(slackUserId, projectKey.toUpperCase())
                .map(ProjectFieldMapping::getProgressFieldId);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize allowed Jira values", e);
        }
    }
}
