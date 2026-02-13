package com.mlorenc.slack.jira.bot.repository;

import com.mlorenc.slack.jira.bot.model.OAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {
}
