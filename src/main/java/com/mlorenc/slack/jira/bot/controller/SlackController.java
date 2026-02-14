package com.mlorenc.slack.jira.bot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlorenc.slack.jira.bot.config.BotProperties;
import com.mlorenc.slack.jira.bot.core.SlackService;
import com.mlorenc.slack.jira.bot.core.SlackSignatureVerifier;
import com.mlorenc.slack.jira.bot.service.JiraFieldService;
import com.mlorenc.slack.jira.bot.service.JiraOAuthService;
import com.mlorenc.slack.jira.bot.service.ProjectMappingService;
import com.mlorenc.slack.jira.bot.service.JiraIssueClient;
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
    private final ProjectMappingService projectMappingService;
    private final JiraFieldService jiraFieldService;
    private final JiraIssueClient jiraIssueClient;
    private final BotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackController(SlackSignatureVerifier verifier,
                           SlackService slackService,
                           JiraOAuthService jiraOAuthService,
                           ProjectMappingService projectMappingService,
                           JiraFieldService jiraFieldService,
                           JiraIssueClient jiraIssueClient,
                           BotProperties properties) {
        this.verifier = verifier;
        this.slackService = slackService;
        this.jiraOAuthService = jiraOAuthService;
        this.projectMappingService = projectMappingService;
        this.jiraFieldService = jiraFieldService;
        this.jiraIssueClient = jiraIssueClient;
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
            if (jiraOAuthService.isConnectedAndTokenValid(slackUserId)) {
                log.atInfo().addKeyValue("event", "slack.command.jira.connect.already_connected")
                        .addKeyValue("slackUserId", slackUserId)
                        .log("Skipped Jira OAuth connect because user is already connected");
                return jsonText("You're already connected to Jira.");
            }

            String authorizeUrl = jiraOAuthService.createAuthorizationUrl(slackUserId);
            slackService.openConnectModal(properties.slack().botToken(), triggerId, authorizeUrl);
            log.atInfo().addKeyValue("event", "slack.command.jira.connect").addKeyValue("slackUserId", slackUserId).log("Handled /jira connect");
            return jsonText("Opening Jira connect modal...");
        }

        if ("reconnect".equalsIgnoreCase(text)) {
            String authorizeUrl = jiraOAuthService.createAuthorizationUrl(slackUserId);
            slackService.openConnectModal(properties.slack().botToken(), triggerId, authorizeUrl);
            log.atInfo().addKeyValue("event", "slack.command.jira.reconnect").addKeyValue("slackUserId", slackUserId).log("Handled /jira reconnect");
            return jsonText("Opening Jira reconnect modal...");
        }

        if ("map".equalsIgnoreCase(text)) {
            slackService.openProjectMappingModal(properties.slack().botToken(), triggerId);
            return jsonText("Opening project mapping modal...");
        }

        UpdateCommand updateCommand = parseUpdateCommand(text);
        if (updateCommand != null) {
            return handleUpdateCommand(slackUserId, updateCommand);
        }

        MapFieldCommand mapFieldCommand = parseMapFieldCommand(text);
        if (mapFieldCommand != null) {
            projectMappingService.saveMapping(slackUserId, mapFieldCommand.projectKey(), mapFieldCommand.fieldId(), "", List.of());
            return jsonText("Saved field mapping for project " + mapFieldCommand.projectKey().toUpperCase() + ".");
        }

        LogWorkCommand logWorkCommand = parseLogWorkCommand(text);
        if (logWorkCommand != null) {
            return handleLogWorkCommand(slackUserId, logWorkCommand);
        }

        return jsonText("Usage: /ml-jira connect OR /ml-jira reconnect OR /ml-jira map OR /ml-jira update <ISSUE-1> <value> OR /ml-jira map-field <PROJECT> <fieldId> OR /ml-jira logwork <ISSUE-1> <timeSpent> [comment]");
    }

    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String interactions(@RequestHeader("X-Slack-Request-Timestamp") String ts,
                               @RequestHeader("X-Slack-Signature") String sig,
                               @RequestBody String rawBody) throws Exception {
        log.info("Slack interactions hit. rawBody startsWith={}", rawBody.substring(0, Math.min(rawBody.length(), 120)));

        if (!verifier.verify(properties.slack().signingSecret(), ts, sig, rawBody)) {
            return "";
        }

        Map<String, String> form = parseForm(rawBody);
        JsonNode payload = objectMapper.readTree(form.get("payload"));
        String payloadType = payload.path("type").asText();

        if ("block_suggestion".equals(payloadType)) {
            String slackUserId = payload.path("user").path("id").asText();
            String value = payload.path("value").asText("");
            try {
                List<JiraFieldService.FieldOption> fields = jiraFieldService.searchFields(slackUserId, value);
                return optionsJson(fields);
            } catch (Exception e) {
                log.atWarn().setCause(e)
                        .addKeyValue("event", "slack.interaction.field_suggest.error")
                        .addKeyValue("slackUserId", slackUserId)
                        .log("Failed to fetch Jira field suggestions for Slack interaction");
                return "{\"options\":[]}";
            }
        }

        if ("view_submission".equals(payloadType)
                && "jira_mapping_submit".equals(payload.path("view").path("callback_id").asText())) {
            SlackService.MappingSubmission submission = slackService.parseMappingSubmission(payload);
            JiraFieldService.FieldOption selectedField = jiraFieldService
                    .findFieldById(submission.slackUserId(), submission.progressFieldId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected Jira field was not found"));

            projectMappingService.saveMapping(
                    submission.slackUserId(),
                    submission.projectKey(),
                    submission.progressFieldId(),
                    selectedField.type(),
                    selectedField.allowedValues());
            log.atInfo().addKeyValue("event", "slack.interaction.mapping.saved")
                    .addKeyValue("slackUserId", submission.slackUserId())
                    .addKeyValue("projectKey", submission.projectKey())
                    .log("Saved Jira project mapping from Slack modal");
        }

        return "";
    }

    private String handleUpdateCommand(String slackUserId, UpdateCommand command) {
        try {
            String cloudId = jiraOAuthService.findConnectedJiraCloudId(slackUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Please connect Jira first using /ml-jira connect"));
            String accessToken = jiraOAuthService.getValidAccessToken(slackUserId);
            String projectKey = jiraIssueClient.fetchIssueProjectKey(cloudId, accessToken, command.issueKey());

            Optional<String> mappedField = projectMappingService.getProgressField(slackUserId, projectKey);
            if (mappedField.isPresent()) {
                jiraIssueClient.updateIssueField(cloudId, accessToken, command.issueKey(), mappedField.get(), command.value());
                return jsonText("Updated issue " + command.issueKey() + " using mapped field " + mappedField.get() + ".");
            }

            List<JiraFieldService.FieldOption> fields = jiraFieldService.fetchAllFields(slackUserId);
            String suggested = fields.stream()
                    .limit(15)
                    .map(field -> field.name() + " (" + field.id() + ")")
                    .collect(Collectors.joining(", "));
            String message = "No mapped field found for project " + projectKey + ". Use /ml-jira map-field " + projectKey + " <fieldId>. Available fields: " + (suggested.isBlank() ? "none" : suggested);
            return jsonText(message);
        } catch (Exception ex) {
            log.atWarn().setCause(ex)
                    .addKeyValue("event", "slack.command.jira.update.error")
                    .addKeyValue("slackUserId", slackUserId)
                    .addKeyValue("issueKey", command.issueKey())
                    .log("Failed to update Jira mapped field from slash command");
            return jsonText("Could not update Jira issue: " + ex.getMessage());
        }
    }

    private String handleLogWorkCommand(String slackUserId, LogWorkCommand command) {
        try {
            String cloudId = jiraOAuthService.findConnectedJiraCloudId(slackUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Please connect Jira first using /ml-jira connect"));
            String accessToken = jiraOAuthService.getValidAccessToken(slackUserId);
            jiraIssueClient.logWork(cloudId, accessToken, command.issueKey(), command.timeSpent(), command.comment());
            return jsonText("Logged " + command.timeSpent() + " on " + command.issueKey() + ".");
        } catch (Exception ex) {
            log.atWarn().setCause(ex)
                    .addKeyValue("event", "slack.command.jira.logwork.error")
                    .addKeyValue("slackUserId", slackUserId)
                    .addKeyValue("issueKey", command.issueKey())
                    .log("Failed to log Jira work from slash command");
            return jsonText("Could not log work: " + ex.getMessage());
        }
    }

    private String optionsJson(List<JiraFieldService.FieldOption> fields) throws Exception {
        List<Map<String, Object>> options = fields.stream()
                .map(field -> Map.<String, Object>of(
                        "text", Map.of("type", "plain_text", "text", field.name()),
                        "value", field.id(),
                        "description", Map.of("type", "plain_text", "text", optionDescription(field))))
                .toList();
        return objectMapper.writeValueAsString(Map.of("options", options));
    }

    private static String optionDescription(JiraFieldService.FieldOption field) {
        String type = field.type() == null || field.type().isBlank() ? "unknown" : field.type();
        int allowedCount = field.allowedValues() == null ? 0 : field.allowedValues().size();
        return allowedCount > 0 ? "type=" + type + ", allowed=" + allowedCount : "type=" + type;
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

    static UpdateCommand parseUpdateCommand(String text) {
        String[] tokens = text.split("\\s+", 3);
        if (tokens.length < 3 || !"update".equalsIgnoreCase(tokens[0])) {
            return null;
        }
        return new UpdateCommand(tokens[1], tokens[2]);
    }

    static MapFieldCommand parseMapFieldCommand(String text) {
        String[] tokens = text.split("\\s+");
        if (tokens.length != 3 || !"map-field".equalsIgnoreCase(tokens[0])) {
            return null;
        }
        return new MapFieldCommand(tokens[1], tokens[2]);
    }

    static LogWorkCommand parseLogWorkCommand(String text) {
        String[] tokens = text.split("\\s+", 4);
        if (tokens.length < 3 || !"logwork".equalsIgnoreCase(tokens[0])) {
            return null;
        }
        String comment = tokens.length > 3 ? tokens[3] : "";
        return new LogWorkCommand(tokens[1], tokens[2], comment);
    }

    record UpdateCommand(String issueKey, String value) { }

    record MapFieldCommand(String projectKey, String fieldId) { }

    record LogWorkCommand(String issueKey, String timeSpent, String comment) { }
}
