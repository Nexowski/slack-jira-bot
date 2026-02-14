package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.core.SlackSignatureVerifier;
import com.mlorenc.slack.jira.bot.service.JiraFieldService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import com.mlorenc.slack.jira.bot.service.ProjectMappingService;
import com.mlorenc.slack.jira.bot.service.JiraIssueClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlackController.class)
class SlackControllerWebTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SlackSignatureVerifier verifier;
    @MockBean
    private SlackService slackService;
    @MockBean
    private JiraOAuthService jiraOAuthService;
    @MockBean
    private ProjectMappingService projectMappingService;
    @MockBean
    private JiraFieldService jiraFieldService;
    @MockBean
    private JiraIssueClient jiraIssueClient;
    @MockBean
    private BotProperties properties;

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=connect&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid Slack signature")));
    }

    @Test
    void shouldReturnEmptyOptionsOnFieldSuggestionError() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraFieldService.searchFields("U1", "prog")).thenThrow(new RuntimeException("401"));

        String payload = """
                {
                  "type":"block_suggestion",
                  "user":{"id":"U1"},
                  "value":"prog"
                }
                """;

        String body = "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);

        mockMvc.perform(post("/slack/interactions")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"options\":[]}"));
    }

    @Test
    void shouldReturnOptionsForFieldSuggestion() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraFieldService.searchFields("U1", "prog"))
                .thenReturn(List.of(new JiraFieldService.FieldOption("customfield_10042", "Progress", "number", List.of("1", "2"))));

        String payload = """
                {
                  "type":"block_suggestion",
                  "user":{"id":"U1"},
                  "value":"prog"
                }
                """;

        String body = "payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);

        mockMvc.perform(post("/slack/interactions")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"options":[{"text":{"type":"plain_text","text":"Progress"},"value":"customfield_10042","description":{"type":"plain_text","text":"type=number, allowed=2"}}]}
                        """));
    }


    @Test
    void shouldPersistMapFieldCommand() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=map-field+ABC+customfield_1&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Saved field mapping")));

        verify(projectMappingService).saveMapping("U1", "ABC", "customfield_1", "", List.of());
    }

    @Test
    void shouldUpdateMappedFieldForIssue() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraOAuthService.findConnectedJiraCloudId("U1")).thenReturn(Optional.of("cloud-1"));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token");
        when(jiraIssueClient.fetchIssueProjectKey("cloud-1", "token", "ABC-1")).thenReturn("ABC");
        when(projectMappingService.getProgressField("U1", "ABC")).thenReturn(Optional.of("customfield_10042"));

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=update+ABC-1+In+Review&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Updated issue ABC-1")));

        verify(jiraIssueClient).updateIssueField("cloud-1", "token", "ABC-1", "customfield_10042", "In Review");
    }

    @Test
    void shouldListFieldsWhenProjectFieldNotMapped() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraOAuthService.findConnectedJiraCloudId("U1")).thenReturn(Optional.of("cloud-1"));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token");
        when(jiraIssueClient.fetchIssueProjectKey("cloud-1", "token", "ABC-1")).thenReturn("ABC");
        when(projectMappingService.getProgressField("U1", "ABC")).thenReturn(Optional.empty());
        when(jiraFieldService.fetchAllFields("U1")).thenReturn(List.of(new JiraFieldService.FieldOption("customfield_1", "Progress", "", List.of())));

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=update+ABC-1+In+Review&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No mapped field found")));
    }

    @Test
    void shouldLogWork() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraOAuthService.findConnectedJiraCloudId("U1")).thenReturn(Optional.of("cloud-1"));
        when(jiraOAuthService.getValidAccessToken("U1")).thenReturn("token");

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=logwork+ABC-1+1h30m+tempo+note&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Logged 1h30m on ABC-1")));

        verify(jiraIssueClient).logWork("cloud-1", "token", "ABC-1", "1h30m", "tempo note");
    }

}
