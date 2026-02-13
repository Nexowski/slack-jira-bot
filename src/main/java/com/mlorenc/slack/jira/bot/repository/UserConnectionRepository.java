package com.mlorenc.slack.jira.bot.repository;

import com.mlorenc.slack.jira.bot.model.UserConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserConnectionRepository extends JpaRepository<UserConnection, Long> {
    Optional<UserConnection> findBySlackUserId(String slackUserId);
}
