package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.core.SlackSignatureVerifier;
import com.mlorenc.slack.jira.bot.service.JiraFieldService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import com.mlorenc.slack.jira.bot.service.MlJiraUpdateService;
import com.mlorenc.slack.jira.bot.service.ProjectMappingService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private MlJiraUpdateService mlJiraUpdateService;
    @MockBean
    private BotProperties properties;
    @MockBean
    private JiraFieldService jiraFieldService;

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
    void shouldReturnSuggestionsForProgressField() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraFieldService.searchFields("U1", "prog")).thenReturn(List.of(
                new JiraFieldService.JiraFieldOption("customfield_10042", "Progress")));

        String payload = """
                {
                  "type": "block_suggestion",
                  "user": {"id": "U1"},
                  "block_id": "progress_field_block",
                  "action_id": "progress_field_input",
                  "value": "prog"
                }
                """;

        mockMvc.perform(post("/slack/interactions")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].value").value("customfield_10042"))
                .andExpect(jsonPath("$.options[0].text.text").value("Progress"));
    }

    @Test
    void shouldSaveMappingOnViewSubmission() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        String payload = """
                {
                  "type": "view_submission",
                  "user": {"id": "U1"},
                  "view": {
                    "callback_id": "jira_mapping_submit",
                    "state": {
                      "values": {
                        "project_block": {
                          "project_input": {"value": "abc"}
                        },
                        "progress_field_block": {
                          "progress_field_input": {
                            "selected_option": {
                              "value": "customfield_10042",
                              "text": {"type": "plain_text", "text": "Progress"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        mockMvc.perform(post("/slack/interactions")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("payload=" + URLEncoder.encode(payload, StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(projectMappingService).saveMapping("U1", "abc", "customfield_10042");
    }

    @Test
    void shouldSkipOAuthWhenAlreadyConnected() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraOAuthService.findConnectedJiraCloudId("U1")).thenReturn(Optional.of("cloud-1"));

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=connect&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Already connected")));

        verify(jiraOAuthService, never()).createAuthorizationUrl(anyString());
        verify(slackService, never()).openConnectModal(anyString(), anyString(), anyString());
    }

    @Test
    void shouldStartOAuthForReconnect() throws Exception {
        when(properties.slack()).thenReturn(new BotProperties.Slack("bot-token", "secret"));
        when(verifier.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(jiraOAuthService.createAuthorizationUrl("U1")).thenReturn("https://auth");

        mockMvc.perform(post("/slack/commands")
                        .header("X-Slack-Request-Timestamp", "1")
                        .header("X-Slack-Signature", "sig")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("command=%2Fml-jira&text=reconnect&trigger_id=trig&user_id=U1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Opening Jira reconnect modal")));

        verify(jiraOAuthService).createAuthorizationUrl("U1");
        verify(slackService).openConnectModal("bot-token", "trig", "https://auth");
    }
}
