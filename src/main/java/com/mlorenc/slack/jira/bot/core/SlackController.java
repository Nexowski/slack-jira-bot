package com.mlorenc.slack.jira.bot.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/slack")
public class SlackController {

    private final SlackSignatureVerifier verifier;
    private final SlackService slack;
    private final JiraClient jira;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${bot.slack.botToken}") private String slackBotToken;
    @Value("${bot.slack.signingSecret}") private String slackSigningSecret;

    @Value("${bot.jira.baseUrl}") private String jiraBaseUrl;
    @Value("${bot.jira.email}") private String jiraEmail;
    @Value("${bot.jira.apiToken}") private String jiraToken;
    @Value("${bot.jira.progressFieldId}") private String progressFieldId;

    public SlackController(SlackSignatureVerifier verifier, SlackService slack, JiraClient jira) {
        this.verifier = verifier;
        this.slack = slack;
        this.jira = jira;
    }

    /**
     * Slash command endpoint.
     * - /progress              -> opens modal
     * - /progress SCRUM-3 10   -> does async Jira update and updates ephemeral message via response_url
     */
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String commands(@RequestHeader("X-Slack-Request-Timestamp") String ts,
                           @RequestHeader("X-Slack-Signature") String sig,
                           @RequestBody String rawBody) throws Exception {

        if (!verifier.verify(slackSigningSecret, ts, sig, rawBody)) {
            // Slack expects 200 OK; returning message is fine
            return "{\"response_type\":\"ephemeral\",\"text\":\"Invalid Slack signature.\"}";
        }

        Map<String, String> form = parseForm(rawBody);

        String command = form.getOrDefault("command", "");
        String triggerId = form.getOrDefault("trigger_id", "");
        String text = form.getOrDefault("text", "").trim();
        String responseUrl = form.getOrDefault("response_url", "");
        String slackUserId = form.getOrDefault("user_id", "unknown");

        if (!"/progress".equals(command)) {
            return "{\"response_type\":\"ephemeral\",\"text\":\"Unknown command.\"}";
        }

        // No args => open modal (existing behaviour)
        if (text.isBlank()) {
            slack.openProgressModal(slackBotToken, triggerId);
            // empty body OK
            return "{\"response_type\":\"ephemeral\",\"text\":\"Opening form...\"}";
        }

        // Args provided => run async, respond immediately to avoid Slack timeout
        ParsedArgs args;
        try {
            args = ParsedArgs.parse(text);
        } catch (Exception e) {
            String msg = "Usage: /progress SCRUM-3 10  (or multiple keys: /progress SCRUM-3,SCRUM-4 10)";
            return "{\"response_type\":\"ephemeral\",\"text\":" + om.writeValueAsString(msg) + "}";
        }

        final ParsedArgs finalArgs = args;

        // Immediate ack
        String startText = renderProgress("⏳ Working on it…", 0, Math.max(1, finalArgs.issueKeys.size()));
        // Return ephemeral immediately
        String initial = """
        {
          "response_type": "ephemeral",
          "text": %s
        }
        """.formatted(om.writeValueAsString(startText));

        // Do Jira work asynchronously, updating response_url as we go
        CompletableFuture.runAsync(() -> {
            try {
                if (responseUrl == null || responseUrl.isBlank()) {
                    return; // can't update message, but still do Jira updates
                }

                slack.updateResponseUrl(responseUrl, renderProgress("✅ Validating input…", 0, finalArgs.issueKeys.size()));

                int total = finalArgs.issueKeys.size();
                int done = 0;

                for (String issueKey : finalArgs.issueKeys) {
                    String stepText = renderProgress("🔄 Updating " + issueKey + " → " + finalArgs.percent + "%", done, total);
                    slack.updateResponseUrl(responseUrl, stepText);

                    String comment = "Progress update from Slack\n"
                            + "User: <@" + slackUserId + ">\n"
                            + "Percent complete: " + finalArgs.percent + "%";

                    jira.setPercentComplete(jiraBaseUrl, jiraEmail, jiraToken, issueKey, progressFieldId, finalArgs.percent);
                    jira.addComment(jiraBaseUrl, jiraEmail, jiraToken, issueKey, comment);

                    done++;
                    slack.updateResponseUrl(responseUrl, renderProgress("✅ Updated " + issueKey, done, total));
                }

                String finalMsg = "🎉 Done. Updated: " + String.join(", ", finalArgs.issueKeys)
                        + " → " + finalArgs.percent + "%";
                slack.updateResponseUrl(responseUrl, renderProgress(finalMsg, total, total));

            } catch (Exception ex) {
                try {
                    String err = "❌ Failed: " + ex.getMessage();
                    slack.updateResponseUrl(responseUrl, err);
                } catch (Exception ignored) {
                    // ignore secondary failures
                }
            }
        });

        return initial;
    }

    // Interactivity payload is x-www-form-urlencoded with a "payload" JSON field
    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String interactions(@RequestHeader("X-Slack-Request-Timestamp") String ts,
                               @RequestHeader("X-Slack-Signature") String sig,
                               @RequestBody String rawBody) throws Exception {

        if (!verifier.verify(slackSigningSecret, ts, sig, rawBody)) {
            return "";
        }

        Map<String, String> form = parseForm(rawBody);
        String payloadJson = form.get("payload");
        JsonNode payload = om.readTree(payloadJson);

        String type = payload.path("type").asText();
        if ("view_submission".equals(type)
                && "progress_submit".equals(payload.path("view").path("callback_id").asText())) {

            var submission = slack.parseSubmission(payload);
            List<String> issueKeys = parseIssueKeys(submission.issueKeysRaw());

            String comment = "Progress update from Slack\n"
                    + "User: <@" + submission.slackUserId() + ">\n"
                    + "Percent complete: " + submission.percent() + "%\n"
                    + "Blocked: " + (submission.blocked() ? "YES" : "no") + "\n"
                    + (submission.note() == null || submission.note().isBlank() ? "" : ("Note: " + submission.note()));

            for (String issueKey : issueKeys) {
                jira.setPercentComplete(jiraBaseUrl, jiraEmail, jiraToken, issueKey, progressFieldId, submission.percent());
                jira.addComment(jiraBaseUrl, jiraEmail, jiraToken, issueKey, comment);
            }

            // ACK
            return "";
        }

        return "";
    }

    private static List<String> parseIssueKeys(String raw) {
        return Arrays.stream(raw.toUpperCase().split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> s.matches("[A-Z][A-Z0-9]+-\\d+"))
                .distinct()
                .collect(Collectors.toList());
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

    private static String renderProgress(String title, int done, int total) {
        // simple text progress bar (works everywhere in Slack)
        int width = 10;
        int filled = total <= 0 ? 0 : (int) Math.round((done * 1.0 / total) * width);
        if (filled < 0) filled = 0;
        if (filled > width) filled = width;

        String bar = "█".repeat(filled) + "░".repeat(width - filled);
        return title + "\n" + "`" + bar + "` " + done + "/" + total;
    }

    static class ParsedArgs {
        final List<String> issueKeys;
        final int percent;

        ParsedArgs(List<String> issueKeys, int percent) {
            this.issueKeys = issueKeys;
            this.percent = percent;
        }

        static ParsedArgs parse(String text) {
            String t = text.toUpperCase().trim();

            // last token is percent
            String[] parts = t.split("\\s+");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Need issue key(s) and percent");
            }

            int percent = clampPercent(parts[parts.length - 1]);

            String keysPart = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
            List<String> keys = Arrays.stream(keysPart.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> s.matches("[A-Z][A-Z0-9]+-\\d+"))
                    .distinct()
                    .toList();

            if (keys.isEmpty()) throw new IllegalArgumentException("No valid issue keys");
            return new ParsedArgs(keys, percent);
        }

        static int clampPercent(String s) {
            return com.mlorenc.slack.jira.bot.core.ParsedArgs.clampPercent(s);
        }
    }
}
