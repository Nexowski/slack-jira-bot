package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.UserConnection;
import com.mlorenc.slack.jira.bot.repository.UserConnectionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MlJiraUpdateServiceTest {

    private final JiraOAuthService jiraOAuthService = mock(JiraOAuthService.class);
    private final ProjectMappingService projectMappingService = mock(ProjectMappingService.class);
    private final UserConnectionRepository userConnectionRepository = mock(UserConnectionRepository.class);
    private final JiraIssueClient jiraIssueClient = mock(JiraIssueClient.class);

    private final MlJiraUpdateService service = new MlJiraUpdateService(
            jiraOAuthService,
            projectMappingService,
            userConnectionRepository,
            jiraIssueClient
    );

    @Test
    void shouldUpdateUsingMappedFieldId() {
        UserConnection connection = connection("U1", "cloud-1");
        when(userConnectionRepository.findBySlackUserId("U1")).thenReturn(Optional.of(connection));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token-123");
        when(jiraIssueClient.fetchIssueProjectKey("cloud-1", "token-123", "ABC-1")).thenReturn("ABC");
        when(projectMappingService.getProgressField("U1", "ABC")).thenReturn(Optional.of("customfield_10042"));

        String response = service.handleUpdate("U1", "abc-1", "45");

        verify(jiraIssueClient).updateIssueField("cloud-1", "token-123", "ABC-1", "customfield_10042", "45");
        assertThat(response).contains("Updated ABC-1 in project ABC").contains("Progress:");
    }

    @Test
    void shouldResolveMappedFieldNameBeforeUpdate() {
        UserConnection connection = connection("U1", "cloud-1");
        when(userConnectionRepository.findBySlackUserId("U1")).thenReturn(Optional.of(connection));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token-123");
        when(jiraIssueClient.fetchIssueProjectKey("cloud-1", "token-123", "ABC-1")).thenReturn("ABC");
        when(projectMappingService.getProgressField("U1", "ABC")).thenReturn(Optional.of("Progress"));
        when(jiraIssueClient.findFieldIdByName("cloud-1", "token-123", "Progress")).thenReturn(Optional.of("customfield_20000"));

        service.handleUpdate("U1", "ABC-1", "In QA");

        verify(jiraIssueClient).updateIssueField("cloud-1", "token-123", "ABC-1", "customfield_20000", "In QA");
    }

    @Test
    void shouldReturnClearErrorWhenMappingMissing() {
        UserConnection connection = connection("U1", "cloud-1");
        when(userConnectionRepository.findBySlackUserId("U1")).thenReturn(Optional.of(connection));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token-123");
        when(jiraIssueClient.fetchIssueProjectKey("cloud-1", "token-123", "ABC-1")).thenReturn("ABC");
        when(projectMappingService.getProgressField("U1", "ABC")).thenReturn(Optional.empty());

        String response = service.handleUpdate("U1", "ABC-1", "20");

        assertThat(response).isEqualTo("No mapping found for project ABC. Use /ml-jira map.");
        verify(jiraIssueClient, never()).updateIssueField(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static UserConnection connection(String slackUserId, String cloudId) {
        UserConnection userConnection = new UserConnection();
        userConnection.setSlackUserId(slackUserId);
        userConnection.setJiraCloudId(cloudId);
        userConnection.setJiraAccountId("acc");
        userConnection.setConnectedAt(Instant.now());
        return userConnection;
    }
}
