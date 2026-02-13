package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/jira/oauth2")
public class JiraOAuthController {

    private final JiraOAuthService jiraOAuthService;

    public JiraOAuthController(JiraOAuthService jiraOAuthService) {
        this.jiraOAuthService = jiraOAuthService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("slackUserId") String slackUserId) {
        String url = jiraOAuthService.createAuthorizationUrl(slackUserId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam("code") String code,
                                           @RequestParam("state") String state) {
        jiraOAuthService.handleCallback(code, state);
        return ResponseEntity.ok("Jira connection completed. You can close this window.");
    }
}
