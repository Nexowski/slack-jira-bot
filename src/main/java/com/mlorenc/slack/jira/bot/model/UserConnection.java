package com.mlorenc.slack.jira.bot.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_connections")
public class UserConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slackUserId;

    @Column(nullable = false)
    private String jiraAccountId;

    @Column(nullable = false)
    private String jiraCloudId;

    @Column(nullable = false)
    private Instant connectedAt;

    public Long getId() { return id; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String slackUserId) { this.slackUserId = slackUserId; }
    public String getJiraAccountId() { return jiraAccountId; }
    public void setJiraAccountId(String jiraAccountId) { this.jiraAccountId = jiraAccountId; }
    public String getJiraCloudId() { return jiraCloudId; }
    public void setJiraCloudId(String jiraCloudId) { this.jiraCloudId = jiraCloudId; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
}
