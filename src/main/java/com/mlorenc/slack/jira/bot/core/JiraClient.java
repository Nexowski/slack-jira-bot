package com.mlorenc.slack.jira.bot.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class JiraClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public void setPercentComplete(String baseUrl, String email, String token, String issueKey, String fieldId, int percent)
            throws Exception {

        String body = """
        {
          "fields": {
            "%s": %d
          }
        }
        """.formatted(fieldId, percent);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(stripSlash(baseUrl) + "/rest/api/3/issue/" + issueKey))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", basicAuth(email, token))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Jira update failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    public void addComment(String baseUrl, String email, String token, String issueKey, String commentText)
            throws Exception {

        // Jira Cloud v3 expects Atlassian Document Format (ADF)
        String body = """
        {
          "body": {
            "type": "doc",
            "version": 1,
            "content": [
              {
                "type": "paragraph",
                "content": [
                  { "type": "text", "text": %s }
                ]
              }
            ]
          }
        }
        """.formatted(om.writeValueAsString(commentText)); // safe JSON escaping

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(stripSlash(baseUrl) + "/rest/api/3/issue/" + issueKey + "/comment"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", basicAuth(email, token))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Jira comment failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private static String basicAuth(String email, String token) {
        String raw = email + ":" + token;
        String b64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + b64;
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
