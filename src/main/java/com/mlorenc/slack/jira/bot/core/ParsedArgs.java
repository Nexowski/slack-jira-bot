package com.mlorenc.slack.jira.bot.core;

public class ParsedArgs {
    final java.util.List<String> issueKeys;
    final int percent;

    ParsedArgs(java.util.List<String> issueKeys, int percent) {
        this.issueKeys = issueKeys;
        this.percent = percent;
    }

    static ParsedArgs parse(String text) {
        // Accept: "SCRUM-3 10" or "SCRUM-3,SCRUM-4 10" or "SCRUM-3 SCRUM-4 10"
        String t = text.toUpperCase().trim();

        // last token = percent
        String[] parts = t.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Usage: /progress SCRUM-3 10");
        }
        int percent = clampPercent(parts[parts.length - 1]);

        // everything except last token = issue keys segment
        String keysPart = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
        java.util.List<String> keys = java.util.Arrays.stream(keysPart.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> s.matches("[A-Z][A-Z0-9]+-\\d+"))
                .distinct()
                .toList();

        if (keys.isEmpty()) throw new IllegalArgumentException("No valid issue keys found.");
        return new ParsedArgs(keys, percent);
    }

    static int clampPercent(String s) {
        int v = Integer.parseInt(s.trim());
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }
}

