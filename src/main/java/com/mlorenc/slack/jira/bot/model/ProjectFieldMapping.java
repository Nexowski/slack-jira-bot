package com.mlorenc.slack.jira.bot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "project_field_mappings")
public class ProjectFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String slackUserId;

    @Column(nullable = false)
    private String jiraProjectKey;

    @Column(nullable = false)
    private String progressFieldId;

    public Long getId() { return id; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String slackUserId) { this.slackUserId = slackUserId; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public void setJiraProjectKey(String jiraProjectKey) { this.jiraProjectKey = jiraProjectKey; }
    public String getProgressFieldId() { return progressFieldId; }
    public void setProgressFieldId(String progressFieldId) { this.progressFieldId = progressFieldId; }
}
