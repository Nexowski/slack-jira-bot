package com.mlorenc.slack.jira.bot.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackControllerTest {

    @Test
    void shouldParseUpdateCommandWithMultiWordValue() {
        SlackController.UpdateCommand command = SlackController.parseUpdateCommand("update ABC-123 In Review");

        assertThat(command).isNotNull();
        assertThat(command.issueKey()).isEqualTo("ABC-123");
        assertThat(command.value()).isEqualTo("In Review");
    }

    @Test
    void shouldReturnNullForNonUpdateCommand() {
        assertThat(SlackController.parseUpdateCommand("map")).isNull();
    }
}
