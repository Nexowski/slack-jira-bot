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

    public void openConnectModal(String botToken, String triggerId, String authorizeUrl) throws Exception {
        String modalJson = """
        {
          "trigger_id": "%s",
          "view": {
            "type": "modal",
            "callback_id": "jira_connect_modal",
            "title": { "type": "plain_text", "text": "Connect Jira" },
            "close": { "type": "plain_text", "text": "Close" },
            "blocks": [
              {
                "type": "section",
                "text": {
                  "type": "mrkdwn",
                  "text": "Click to connect your Jira account using OAuth 3LO."
                }
              },
              {
                "type": "actions",
                "elements": [
                  {
                    "type": "button",
                    "text": { "type": "plain_text", "text": "Connect Jira" },
                    "url": %s,
                    "action_id": "open_jira_oauth"
                  }
                ]
              }
            ]
          }
        }
        """.formatted(escapeJson(triggerId), om.writeValueAsString(authorizeUrl));

        sendViewsOpen(botToken, modalJson);
    }

    public void openProjectMappingModal(String botToken, String triggerId) throws Exception {
        String modalJson = """
        {
          "trigger_id": "%s",
          "view": {
            "type": "modal",
            "callback_id": "jira_mapping_submit",
            "title": { "type": "plain_text", "text": "Jira Field Mapping" },
            "submit": { "type": "plain_text", "text": "Save" },
            "close": { "type": "plain_text", "text": "Cancel" },
            "blocks": [
              {
                "type": "input",
                "block_id": "project_block",
                "label": { "type": "plain_text", "text": "Project key" },
                "element": { "type": "plain_text_input", "action_id": "project_input", "placeholder": { "type": "plain_text", "text": "ABC" } }
              },
              {
                "type": "input",
                "block_id": "progress_field_block",
                "label": { "type": "plain_text", "text": "Progress field" },
                "element": {
                  "type": "external_select",
                  "action_id": "progress_field_input",
                  "min_query_length": 0,
                  "placeholder": { "type": "plain_text", "text": "Search Jira field" }
                }
              }
            ]
          }
        }
        """.formatted(escapeJson(triggerId));

        sendViewsOpen(botToken, modalJson);
    }

    public MappingSubmission parseMappingSubmission(JsonNode payload) {
        String slackUserId = payload.path("user").path("id").asText();
        JsonNode values = payload.path("view").path("state").path("values");
        String projectKey = values.path("project_block").path("project_input").path("value").asText("");
        JsonNode progressFieldInput = values.path("progress_field_block").path("progress_field_input");
        String progressFieldId = progressFieldInput.path("selected_option").path("value").asText("");
        if (progressFieldId.isBlank()) {
            progressFieldId = progressFieldInput.path("value").asText("");
        }
        return new MappingSubmission(slackUserId, projectKey, progressFieldId);
    }

    private void sendViewsOpen(String botToken, String payload) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://slack.com/api/views.open"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + botToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode body = om.readTree(resp.body());
        if (!body.path("ok").asBoolean(false)) {
            throw new RuntimeException("Slack views.open failed: " + resp.body());
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record MappingSubmission(String slackUserId, String projectKey, String progressFieldId) {
    }
}
