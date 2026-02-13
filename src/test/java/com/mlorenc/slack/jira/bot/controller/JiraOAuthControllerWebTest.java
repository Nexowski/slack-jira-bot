package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraOAuthController.class)
class JiraOAuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JiraOAuthService jiraOAuthService;

    @Test
    void shouldRedirectToAuthorizeUrl() throws Exception {
        when(jiraOAuthService.createAuthorizationUrl("U1")).thenReturn("https://auth.example");

        mockMvc.perform(get("/jira/oauth2/authorize").param("slackUserId", "U1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://auth.example"));
    }

    @Test
    void shouldHandleCallback() throws Exception {
        mockMvc.perform(get("/jira/oauth2/callback").param("code", "abc").param("state", "state"))
                .andExpect(status().isOk());

        verify(jiraOAuthService).handleCallback("abc", "state");
    }
}
