package com.mlorenc.slack.jira.bot.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class SlackService {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public void openProgressModal(String botToken, String triggerId) throws Exception {
        String modalJson = """
        {
          "trigger_id": "%s",
          "view": {
            "type": "modal",
            "callback_id": "progress_submit",
            "title": { "type": "plain_text", "text": "Jira progress" },
            "submit": { "type": "plain_text", "text": "Submit" },
            "close": { "type": "plain_text", "text": "Cancel" },
            "blocks": [
              {
                "type": "input",
                "block_id": "issues_block",
                "label": { "type": "plain_text", "text": "Jira issue keys" },
                "element": { "type": "plain_text_input", "action_id": "issues_input", "placeholder": { "type": "plain_text", "text": "ABC-123, ABC-130" } }
              },
              {
                "type": "input",
                "block_id": "percent_block",
                "label": { "type": "plain_text", "text": "%% complete" },
                "element": { "type": "plain_text_input", "action_id": "percent_input", "placeholder": { "type": "plain_text", "text": "0-100" } }
              },
              {
                "type": "input",
                "block_id": "blocked_block",
                "optional": true,
                "label": { "type": "plain_text", "text": "Blocked? (optional)" },
                "element": {
                  "type": "static_select",
                  "action_id": "blocked_select",
                  "options": [
                    { "text": { "type": "plain_text", "text": "No" }, "value": "no" },
                    { "text": { "type": "plain_text", "text": "Yes" }, "value": "yes" }
                  ]
                }
              },
              {
                "type": "input",
                "block_id": "note_block",
                "optional": true,
                "label": { "type": "plain_text", "text": "Note (optional)" },
                "element": { "type": "plain_text_input", "action_id": "note_input", "multiline": true }
              }
            ]
          }
        }
        """.formatted(escapeJson(triggerId));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://slack.com/api/views.open"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + botToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(modalJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode body = om.readTree(resp.body());
        if (!body.path("ok").asBoolean(false)) {
            throw new RuntimeException("Slack views.open failed: " + resp.body());
        }
    }

    /**
     * Update the slash-command ephemeral message using Slack response_url.
     * This avoids needing channel IDs and avoids Slack timeouts.
     */
    public void updateResponseUrl(String responseUrl, String text) throws Exception {
        // Slack expects JSON body like {"text":"...","replace_original":true}
        String payload = """
        {
          "replace_original": true,
          "text": %s
        }
        """.formatted(om.writeValueAsString(text));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Slack response_url update failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public record ProgressSubmission(String slackUserId, String issueKeysRaw, int percent, boolean blocked, String note) {}

    public ProgressSubmission parseSubmission(JsonNode payload) {
        String slackUserId = payload.path("user").path("id").asText();

        JsonNode values = payload.path("view").path("state").path("values");
        String issues = values.path("issues_block").path("issues_input").path("value").asText("");
        String percentStr = values.path("percent_block").path("percent_input").path("value").asText("");
        int percent = safePercent(percentStr);

        String blockedVal = values.path("blocked_block").path("blocked_select").path("selected_option").path("value").asText("no");
        boolean blocked = "yes".equalsIgnoreCase(blockedVal);

        String note = values.path("note_block").path("note_input").path("value").asText("");

        return new ProgressSubmission(slackUserId, issues, percent, blocked, note);
    }

    private static int safePercent(String s) {
        try {
            int v = (int) Math.round(Double.parseDouble(s.trim()));
            if (v < 0) return 0;
            if (v > 100) return 100;
            return v;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
