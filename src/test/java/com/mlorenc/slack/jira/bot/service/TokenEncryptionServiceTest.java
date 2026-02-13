package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.config.BotProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEncryptionServiceTest {

    @Test
    void shouldEncryptAndDecryptToken() {
        BotProperties props = new BotProperties(null, null,
                new BotProperties.Security("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        TokenEncryptionService service = new TokenEncryptionService(props);

        String encrypted = service.encrypt("secret-token");
        String decrypted = service.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo("secret-token");
        assertThat(decrypted).isEqualTo("secret-token");
    }
}
