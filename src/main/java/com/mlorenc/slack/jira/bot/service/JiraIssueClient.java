package com.mlorenc.slack.jira.bot.service;

import java.util.Optional;

public interface JiraIssueClient {
    String fetchIssueProjectKey(String cloudId, String accessToken, String issueKey);

    Optional<String> findFieldIdByName(String cloudId, String accessToken, String fieldName);

    void updateIssueField(String cloudId, String accessToken, String issueKey, String fieldId, String value);

    void logWork(String cloudId, String accessToken, String issueKey, String timeSpent, String comment);
}
