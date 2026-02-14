package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.core.SlackSignatureVerifier;
import com.mlorenc.slack.jira.bot.service.JiraFieldService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
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

import static org.mockito.ArgumentMatchers.anyString;
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
                .thenReturn(List.of(new JiraFieldService.FieldOption("customfield_10042", "Progress")));

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
                        {"options":[{"text":{"type":"plain_text","text":"Progress"},"value":"customfield_10042"}]}
                        """));
    }
}
