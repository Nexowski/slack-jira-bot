package com.mlorenc.slack.jira.bot.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackControllerTest {

    @Test
    void shouldExposeUpdateCommandRecordFields() {
        SlackController.UpdateCommand command = new SlackController.UpdateCommand("ABC-123", "In Review");

        assertThat(command.issueKey()).isEqualTo("ABC-123");
        assertThat(command.value()).isEqualTo("In Review");
    }

    @Test
    void shouldParseUpdateCommandWithMultiWordValue() {
        SlackController.UpdateCommand command = SlackController.parseUpdateCommand("update ABC-123 In Review");

        assertThat(command).isNotNull();
        assertThat(command.issueKey()).isEqualTo("ABC-123");
        assertThat(command.value()).isEqualTo("In Review");
    }

    @Test
    void shouldReturnNullForInvalidUpdateCommand() {
        assertThat(SlackController.parseUpdateCommand("update ABC-123")).isNull();
        assertThat(SlackController.parseUpdateCommand("map-field ABC customfield_1")).isNull();
    }

    @Test
    void shouldParseMapFieldCommand() {
        SlackController.MapFieldCommand command = SlackController.parseMapFieldCommand("map-field ABC customfield_10042");

        assertThat(command).isNotNull();
        assertThat(command.projectKey()).isEqualTo("ABC");
        assertThat(command.fieldId()).isEqualTo("customfield_10042");
    }

    @Test
    void shouldParseLogWorkCommandWithOptionalComment() {
        SlackController.LogWorkCommand command = SlackController.parseLogWorkCommand("logwork ABC-1 2h investigated bug");

        assertThat(command).isNotNull();
        assertThat(command.issueKey()).isEqualTo("ABC-1");
        assertThat(command.timeSpent()).isEqualTo("2h");
        assertThat(command.comment()).isEqualTo("investigated bug");
    }

    @Test
    void shouldParseLogWorkCommandWithoutComment() {
        SlackController.LogWorkCommand command = SlackController.parseLogWorkCommand("logwork ABC-1 30m");

        assertThat(command).isNotNull();
        assertThat(command.comment()).isEmpty();
    }
}
