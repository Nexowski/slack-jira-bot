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
}
