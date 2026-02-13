package com.mlorenc.slack.jira.bot.controller;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/jira/oauth2")
public class JiraOAuthController {

    private static final Logger log = LoggerFactory.getLogger(JiraOAuthController.class);

    private final JiraOAuthService jiraOAuthService;
    private final SlackService slackService;
    private final BotProperties properties;

    public JiraOAuthController(JiraOAuthService jiraOAuthService,
                               SlackService slackService,
                               BotProperties properties) {
        this.jiraOAuthService = jiraOAuthService;
        this.slackService = slackService;
        this.properties = properties;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("slackUserId") String slackUserId) {
        String url = jiraOAuthService.createAuthorizationUrl(slackUserId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam("code") String code,
                                           @RequestParam("state") String state) {
        try {
            JiraOAuthService.CallbackResult result = jiraOAuthService.handleCallback(code, state);
            String confirmation = result.reconnect() ? "✅ Jira reconnected" : "✅ Jira connected";
            slackService.sendEphemeralMessage(properties.slack().botToken(), result.slackUserId(), result.slackUserId(), confirmation);

            return ResponseEntity.ok(successHtml(confirmation));
        } catch (Exception ex) {
            log.atError().addKeyValue("event", "jira.oauth.callback.failed")
                    .addKeyValue("slackUserId", state)
                    .log("Failed handling Jira OAuth callback", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(failureHtml());
        }
    }

    private static String successHtml(String message) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Jira OAuth Complete</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 2rem; color: #1f2937; }
                    .card { max-width: 560px; margin: 2rem auto; border: 1px solid #d1d5db; border-radius: 8px; padding: 1.25rem; }
                    .ok { color: #047857; font-weight: 700; }
                    a { color: #2563eb; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Jira connection complete</h1>
                    <p class="ok">%s</p>
                    <p>You can now return to Slack.</p>
                    <p><a href="https://slack.com/app_redirect">Return to Slack</a></p>
                  </div>
                </body>
                </html>
                """.formatted(message);
    }

    private static String failureHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Jira OAuth Failed</title>
                  <style>
                    body { font-family: Arial, sans-serif; margin: 2rem; color: #1f2937; }
                    .card { max-width: 560px; margin: 2rem auto; border: 1px solid #d1d5db; border-radius: 8px; padding: 1.25rem; }
                    .error { color: #b91c1c; font-weight: 700; }
                    a { color: #2563eb; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Jira connection failed</h1>
                    <p class="error">Please try /ml-jira connect again.</p>
                    <p><a href="https://slack.com/app_redirect">Return to Slack</a></p>
                  </div>
                </body>
                </html>
                """;
    }
}
