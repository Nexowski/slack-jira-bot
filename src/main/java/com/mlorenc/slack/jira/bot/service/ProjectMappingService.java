package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.ProjectFieldMapping;
import com.mlorenc.slack.jira.bot.repository.ProjectFieldMappingRepository;
import org.springframework.stereotype.Service;

@Service
public class ProjectMappingService {

    private final ProjectFieldMappingRepository repository;

    public ProjectMappingService(ProjectFieldMappingRepository repository) {
        this.repository = repository;
    }

    public void saveMapping(String slackUserId, String projectKey, String progressFieldId) {
        ProjectFieldMapping mapping = repository.findBySlackUserIdAndJiraProjectKey(slackUserId, projectKey)
                .orElseGet(ProjectFieldMapping::new);
        mapping.setSlackUserId(slackUserId);
        mapping.setJiraProjectKey(projectKey.toUpperCase());
        mapping.setProgressFieldId(progressFieldId);
        repository.save(mapping);
    }
}
