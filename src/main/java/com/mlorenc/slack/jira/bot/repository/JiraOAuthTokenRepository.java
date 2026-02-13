package com.mlorenc.slack.jira.bot.repository;

import com.mlorenc.slack.jira.bot.model.JiraOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JiraOAuthTokenRepository extends JpaRepository<JiraOAuthToken, Long> {
    Optional<JiraOAuthToken> findBySlackUserId(String slackUserId);
}
