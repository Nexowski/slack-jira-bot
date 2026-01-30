package com.mlorenc.slack.jira.bot.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SlackModels {

    // For modal submission payload
    public record SlackInteractionPayload(
            String type,
            @JsonProperty("user") Map<String, Object> user,
            @JsonProperty("trigger_id") String triggerId,
            @JsonProperty("view") Map<String, Object> view
    ) {}

}
