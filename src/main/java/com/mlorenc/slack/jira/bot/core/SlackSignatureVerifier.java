package com.mlorenc.slack.jira.bot.core;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class SlackSignatureVerifier {

    public boolean verify(String signingSecret, String timestamp, String slackSignature, String rawBody) {
        if (signingSecret == null || timestamp == null || slackSignature == null) return false;

        // Reject very old timestamps (replay protection)
        long ts;
        try { ts = Long.parseLong(timestamp); }
        catch (NumberFormatException e) { return false; }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > 60 * 5) return false;

        String base = "v0:" + timestamp + ":" + rawBody;
        String computed = "v0=" + hmacSha256Hex(signingSecret, base);
        return constantTimeEquals(computed, slackSignature);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC failure", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
