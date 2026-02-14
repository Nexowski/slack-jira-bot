# Slack Jira Bot

Spring Boot bot that connects Slack users to Jira Cloud with Atlassian OAuth 2.0 (3LO), and lets each Slack user map Jira project keys to progress custom fields.

## What this bot does

- Exposes Slack endpoints for slash commands and interactivity.
- Supports `/jira connect` to connect a Slack user to Jira via OAuth.
- Supports `/jira map` to save a per-user mapping via modal:
  - Jira project key (for example `ABC`)
  - Jira progress custom field id (for example `customfield_10042`)
- Supports `/jira map-field <PROJECT> <fieldId>` to map directly from a slash command.
- Supports `/jira update <ISSUE-KEY> <value>` to update the mapped field for an issue.
- Supports `/jira logwork <ISSUE-KEY> <timeSpent> [comment]` to add a Jira worklog entry (visible in Tempo).

## Prerequisites

- Java 21
- A Slack workspace where you can install apps
- A Jira Cloud site + Atlassian account with permission to create OAuth apps
- A public HTTPS URL for local development (for example ngrok)

---

## 1) Create and configure the Atlassian OAuth app (Jira)

This service uses Atlassian OAuth 2.0 authorization code flow (3LO).

1. Open Atlassian Developer Console.
2. Create a new OAuth 2.0 (3LO) app.
3. In app permissions/scopes, add at least:
   - `read:jira-user`
   - `read:jira-work`
   - `write:jira-work`
   - `offline_access`
4. Configure callback URL(s):
   - Local default: `http://localhost:8080/jira/oauth2/callback`
   - If using ngrok, also add: `https://<your-public-url>/jira/oauth2/callback`
5. Copy the app credentials:
   - Client ID -> `JIRA_CLIENT_ID`
   - Client secret -> `JIRA_CLIENT_SECRET`

> Important: `JIRA_REDIRECT_URI` in your environment must exactly match one callback URL configured in Atlassian.

---

## 2) Create and configure the Slack app

1. Go to [api.slack.com/apps](https://api.slack.com/apps) and create an app.
2. Under **OAuth & Permissions**, add bot token scopes:
   - `commands`
   - `chat:write`
3. Install the app to your workspace and copy **Bot User OAuth Token** -> `SLACK_BOT_TOKEN`.
4. Under **Basic Information**, copy **Signing Secret** -> `SLACK_BOT_SIGN_SECRET`.
5. Add a Slash Command:
   - Command: `/jira`
   - Request URL: `https://<your-public-url>/slack/commands`
   - Short description/example: `connect` or `map`
6. Under **Interactivity & Shortcuts**, enable interactivity:
   - Request URL: `https://<your-public-url>/slack/interactions`

### Supported slash commands

- `/jira connect` -> opens a modal with a button to start Atlassian OAuth.
- `/jira map` -> opens a modal to save `projectKey` -> `progressFieldId` mapping.
- `/jira map-field <PROJECT> <fieldId>` -> saves mapping directly from slash command.
- `/jira update <ISSUE-KEY> <value>` -> updates the mapped field for the issue project.
  - If no mapping exists, the bot responds with available Jira fields and suggests mapping command.
- `/jira logwork <ISSUE-KEY> <timeSpent> [comment]` -> logs work time to Jira worklog (Tempo reads this).

---

## 3) Configure environment variables

Set these before running the app:

```bash
export SLACK_BOT_TOKEN='xoxb-...'
export SLACK_BOT_SIGN_SECRET='...'

export JIRA_CLIENT_ID='...'
export JIRA_CLIENT_SECRET='...'
export JIRA_REDIRECT_URI='http://localhost:8080/jira/oauth2/callback'

# Optional: override only if you need non-default Atlassian endpoints/scopes
# export JIRA_SCOPES='read:jira-user read:jira-work write:jira-work offline_access'
# export JIRA_AUTHORIZE_URL='https://auth.atlassian.com/authorize'
# export JIRA_TOKEN_URL='https://auth.atlassian.com/oauth/token'
# export JIRA_RESOURCES_URL='https://api.atlassian.com/oauth/token/accessible-resources'

# 32-byte key in base64 for token encryption at rest
export TOKEN_ENCRYPTION_KEY='MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='
```

Notes:
- H2 database is file-based by default at `./data/bot`.
- If you do not set variables, `application.yml` fallback defaults are used (not suitable for production).

---

## 4) Run locally

```bash
./gradlew bootRun
```

App runs on `http://localhost:8080` by default.

---

## 5) Expose local app to Slack/Atlassian

Example with ngrok:

```bash
ngrok http 8080
```

Take the HTTPS URL ngrok gives you (for example `https://abc123.ngrok-free.app`) and update:

- Slack slash command request URL -> `https://abc123.ngrok-free.app/slack/commands`
- Slack interactivity URL -> `https://abc123.ngrok-free.app/slack/interactions`
- Atlassian OAuth callback URL + `JIRA_REDIRECT_URI` -> `https://abc123.ngrok-free.app/jira/oauth2/callback`

---

## 6) End-to-end smoke test

1. In Slack, run:
   - `/jira connect`
2. In modal, click **Connect Jira**.
3. Complete Atlassian consent in browser.
4. Verify callback success page: `Jira connection completed. You can close this window.`
5. In Slack, run:
   - `/jira map`
6. Save mapping values, for example:
   - Project key: `ABC`
   - Progress field id: `customfield_10042`

If callbacks fail, verify:
- Slack request URLs are reachable publicly over HTTPS.
- `JIRA_REDIRECT_URI` exactly matches Atlassian callback configuration.
- Slack signing secret/token are from the same installed app.
