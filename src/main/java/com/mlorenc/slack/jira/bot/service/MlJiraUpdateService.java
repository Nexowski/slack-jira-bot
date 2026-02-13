package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.UserConnection;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MlJiraUpdateService {

    private static final Logger log = LoggerFactory.getLogger(MlJiraUpdateService.class);
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9]+-\\d+");

    private final JiraOAuthService jiraOAuthService;
    private final ProjectMappingService projectMappingService;
    private final UserConnectionRepository userConnectionRepository;
    private final JiraIssueClient jiraIssueClient;

    public MlJiraUpdateService(JiraOAuthService jiraOAuthService,
                               ProjectMappingService projectMappingService,
                               UserConnectionRepository userConnectionRepository,
                               JiraIssueClient jiraIssueClient) {
        this.jiraOAuthService = jiraOAuthService;
        this.projectMappingService = projectMappingService;
        this.userConnectionRepository = userConnectionRepository;
        this.jiraIssueClient = jiraIssueClient;
    }

    public String handleUpdate(String slackUserId, String issueKeyInput, String value) {
        String issueKey = issueKeyInput == null ? "" : issueKeyInput.trim().toUpperCase(Locale.ROOT);
        String fieldValue = value == null ? "" : value.trim();

        if (!ISSUE_KEY_PATTERN.matcher(issueKey).matches()) {
            return "Invalid issue key. Usage: /ml-jira update ISSUE-KEY <value>";
        }
        if (fieldValue.isBlank()) {
            return "Missing value. Usage: /ml-jira update ISSUE-KEY <value>";
        }

        try {
            UserConnection userConnection = userConnectionRepository.findBySlackUserId(slackUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Your Jira account is not connected. Run /ml-jira connect first."));

            String accessToken = jiraOAuthService.getValidAccessToken(slackUserId);
            String projectKey = jiraIssueClient.fetchIssueProjectKey(userConnection.getJiraCloudId(), accessToken, issueKey);

            String configuredField = projectMappingService.getProgressField(slackUserId, projectKey)
                    .orElseThrow(() -> new IllegalArgumentException("No mapping found for project " + projectKey + ". Use /ml-jira map."));
            String fieldId = resolveFieldId(userConnection.getJiraCloudId(), accessToken, configuredField);

            log.atInfo().addKeyValue("event", "slack.command.mljira.update")
                    .addKeyValue("slackUserId", slackUserId)
                    .addKeyValue("issueKey", issueKey)
                    .addKeyValue("projectKey", projectKey)
                    .log("Updating Jira mapped field from /ml-jira update command");

            jiraIssueClient.updateIssueField(userConnection.getJiraCloudId(), accessToken, issueKey, fieldId, fieldValue);
            return "✅ Updated " + issueKey + " in project " + projectKey + "\n" + progressBar(100);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        } catch (Exception ex) {
            log.atError().setCause(ex)
                    .addKeyValue("event", "slack.command.mljira.update.error")
                    .addKeyValue("slackUserId", slackUserId)
                    .addKeyValue("issueKey", issueKey)
                    .log("Failed to update Jira mapped field");
            return "Failed to update Jira issue. Please verify your mapping and permissions, then try again.";
        }
    }

    private String resolveFieldId(String cloudId, String accessToken, String configuredField) {
        if (configuredField.startsWith("customfield_")) {
            return configuredField;
        }
        return jiraIssueClient.findFieldIdByName(cloudId, accessToken, configuredField)
                .orElseThrow(() -> new IllegalArgumentException("Mapped field name '" + configuredField + "' was not found in Jira."));
    }

    private static String progressBar(int percent) {
        int totalBlocks = 10;
        int filled = Math.round((percent / 100.0f) * totalBlocks);
        return "Progress: " + "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, totalBlocks - filled)) + " " + percent + "%";
    }
}
