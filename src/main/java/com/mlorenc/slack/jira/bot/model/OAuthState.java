package com.mlorenc.slack.jira.bot.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "oauth_states")
public class OAuthState {

    @Id
    private String state;

    @Column(nullable = false)
    private String slackUserId;

    @Column(nullable = false)
    private Instant expiresAt;

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String slackUserId) { this.slackUserId = slackUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
