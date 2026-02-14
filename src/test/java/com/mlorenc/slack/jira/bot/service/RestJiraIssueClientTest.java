package com.mlorenc.slack.jira.bot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RestJiraIssueClientTest {

    @Test
    void shouldSendWorklogPayloadWithComment() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestJiraIssueClient client = new RestJiraIssueClient(restTemplate);

        client.logWork("cloud-1", "token-1", "ABC-1", "1h", "Investigated incident");

        verify(restTemplate).exchange(
                eq("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/issue/ABC-1/worklog"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void shouldSendWorklogPayloadWithoutComment() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestJiraIssueClient client = new RestJiraIssueClient(restTemplate);

        client.logWork("cloud-1", "token-1", "ABC-1", "30m", "");

        verify(restTemplate).exchange(
                eq("https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/issue/ABC-1/worklog"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class));
    }
}
