package com.mlorenc.slack.jira.bot.repository;

import com.mlorenc.slack.jira.bot.model.ProjectFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectFieldMappingRepository extends JpaRepository<ProjectFieldMapping, Long> {
    Optional<ProjectFieldMapping> findBySlackUserIdAndJiraProjectKey(String slackUserId, String jiraProjectKey);
}
