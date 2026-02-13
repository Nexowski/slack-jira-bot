package com.mlorenc.slack.jira.bot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.core.SlackSignatureVerifier;
import com.mlorenc.slack.jira.bot.service.JiraFieldService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import com.mlorenc.slack.jira.bot.service.MlJiraUpdateService;
import com.mlorenc.slack.jira.bot.service.ProjectMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/slack")
public class SlackController {

    private static final Logger log = LoggerFactory.getLogger(SlackController.class);

    private final SlackSignatureVerifier verifier;
    private final SlackService slackService;
    private final JiraOAuthService jiraOAuthService;
    private final JiraFieldService jiraFieldService;
    private final ProjectMappingService projectMappingService;
    private final MlJiraUpdateService mlJiraUpdateService;
    private final BotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackController(SlackSignatureVerifier verifier,
                           SlackService slackService,
                           JiraOAuthService jiraOAuthService,
                           JiraFieldService jiraFieldService,
                           ProjectMappingService projectMappingService,
                           MlJiraUpdateService mlJiraUpdateService,
                           BotProperties properties) {
        this.verifier = verifier;
        this.slackService = slackService;
        this.jiraOAuthService = jiraOAuthService;
        this.jiraFieldService = jiraFieldService;
        this.projectMappingService = projectMappingService;
        this.mlJiraUpdateService = mlJiraUpdateService;
        this.properties = properties;
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String commands(@RequestHeader("X-Slack-Request-Timestamp") String ts,
                           @RequestHeader("X-Slack-Signature") String sig,
                           @RequestBody String rawBody) throws Exception {

        if (!verifier.verify(properties.slack().signingSecret(), ts, sig, rawBody)) {
            return "{\"response_type\":\"ephemeral\",\"text\":\"Invalid Slack signature.\"}";
        }

        Map<String, String> form = parseForm(rawBody);
        String command = form.getOrDefault("command", "");
        String text = form.getOrDefault("text", "").trim();
        String triggerId = form.getOrDefault("trigger_id", "");
        String slackUserId = form.getOrDefault("user_id", "");

        if (!"/jira".equals(command) && !"/ml-jira".equals(command)) {
            return "{\"response_type\":\"ephemeral\",\"text\":\"Unknown command. Use /ml-jira connect, /ml-jira reconnect, or /ml-jira map.\"}";
        }

        if ("connect".equalsIgnoreCase(text)) {
            Optional<String> jiraCloudId = jiraOAuthService.findConnectedJiraCloudId(slackUserId);
            if (jiraCloudId.isPresent()) {
                log.atInfo()
                        .addKeyValue("event", "slack.command.ml-jira.connect.already_connected")
                        .addKeyValue("slackUserId", slackUserId)
                        .addKeyValue("jiraCloudId", jiraCloudId.get())
                        .log("Skipped OAuth flow because user is already connected");
                return jsonText("✅ Already connected. Use /ml-jira reconnect to re-authorize.");
            }

            String authorizeUrl = jiraOAuthService.createAuthorizationUrl(slackUserId);
            slackService.openConnectModal(properties.slack().botToken(), triggerId, authorizeUrl);
            log.atInfo().addKeyValue("event", "slack.command.ml-jira.connect")
                    .addKeyValue("slackUserId", slackUserId)
                    .log("Handled /ml-jira connect");
            return jsonText("Opening Jira connect modal...");
        }

        if ("reconnect".equalsIgnoreCase(text)) {
            String authorizeUrl = jiraOAuthService.createAuthorizationUrl(slackUserId);
            slackService.openConnectModal(properties.slack().botToken(), triggerId, authorizeUrl);
            log.atInfo().addKeyValue("event", "slack.command.ml-jira.reconnect")
                    .addKeyValue("slackUserId", slackUserId)
                    .log("Handled /ml-jira reconnect");
            return jsonText("Opening Jira reconnect modal...");
        }

        if ("map".equalsIgnoreCase(text)) {
            slackService.openProjectMappingModal(properties.slack().botToken(), triggerId);
            return jsonText("Opening project mapping modal...");
        }

        return jsonText("Usage: /ml-jira connect OR /ml-jira map OR /ml-jira update ISSUE-KEY <value>");
    }

    static UpdateCommand parseUpdateCommand(String text) {
        String[] parts = text.trim().split("\\s+", 3);
        if (parts.length < 3 || !"update".equalsIgnoreCase(parts[0])) {
            return null;
        }
        return new UpdateCommand(parts[1], parts[2]);
    }

    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String interactions(@RequestHeader("X-Slack-Request-Timestamp") String ts,
                               @RequestHeader("X-Slack-Signature") String sig,
                               @RequestBody String rawBody) throws Exception {

        if (!verifier.verify(properties.slack().signingSecret(), ts, sig, rawBody)) {
            return "";
        }

        Map<String, String> form = parseForm(rawBody);
        JsonNode payload = objectMapper.readTree(form.get("payload"));

        if ("block_suggestion".equals(payload.path("type").asText())
                && "progress_field_block".equals(payload.path("block_id").asText())
                && "progress_field_input".equals(payload.path("action_id").asText())) {
            String slackUserId = payload.path("user").path("id").asText();
            String query = payload.path("value").asText("");
            List<Map<String, Object>> options = jiraFieldService.searchFields(slackUserId, query).stream()
                    .map(option -> Map.<String, Object>of(
                            "text", Map.of("type", "plain_text", "text", option.name()),
                            "value", option.id()))
                    .toList();
            return objectMapper.writeValueAsString(Map.of("options", options));
        }

        if ("view_submission".equals(payload.path("type").asText())
                && "jira_mapping_submit".equals(payload.path("view").path("callback_id").asText())) {
            SlackService.MappingSubmission submission = slackService.parseMappingSubmission(payload);
            projectMappingService.saveMapping(submission.slackUserId(), submission.projectKey(), submission.progressFieldId());
            log.atInfo().addKeyValue("event", "slack.interaction.mapping.saved")
                    .addKeyValue("slackUserId", submission.slackUserId())
                    .addKeyValue("projectKey", submission.projectKey())
                    .log("Saved Jira project mapping from Slack modal");
        }

        return "";
    }

    private static String jsonText(String text) {
        return "{\"response_type\":\"ephemeral\",\"text\":" + quoteJson(text) + "}";
    }

    private static String quoteJson(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static Map<String, String> parseForm(String rawBody) {
        return Arrays.stream(rawBody.split("&"))
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(
                        kv -> urlDecode(kv[0]),
                        kv -> kv.length > 1 ? urlDecode(kv[1]) : "",
                        (a, b) -> b
                ));
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    record UpdateCommand(String issueKey, String value) {
    }
}
