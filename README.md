# Slack Jira Bot

A Spring Boot app that handles a Slack `/progress` command and updates Jira Cloud issues.

## Prerequisites

- Java 21
- A Slack app with slash commands/interactivity enabled
- A Jira Cloud site, user email, and Jira API token

## 1) How to configure the app to run

### Environment variables

This project reads all runtime configuration from environment variables via `application.yml`:

- `SLACK_BOT_TOKEN`
- `SLACK_BOT_CLIENT_SECRET`
- `SLACK_BOT_SIGN_SECRET`
- `BASE_URL` (your Jira base URL, for example `https://your-domain.atlassian.net`)
- `EMAIL` (Jira account email)
- `API_TOKEN` (Jira API token)
- `FIELD_ID` (Jira custom field id used for progress, for example `customfield_12345`)

Example:

```bash
export SLACK_BOT_TOKEN='xoxb-...'
export SLACK_BOT_CLIENT_SECRET='...'
export SLACK_BOT_SIGN_SECRET='...'
export BASE_URL='https://your-domain.atlassian.net'
export EMAIL='you@company.com'
export API_TOKEN='...'
export FIELD_ID='customfield_12345'
```

### Run locally

```bash
./gradlew bootRun
```

The app starts on port `8080` by default.

### Slack request URLs

Expose your local app (for example with ngrok) and configure Slack to call:

- Slash command request URL: `https://<public-url>/slack/commands`
- Interactivity request URL: `https://<public-url>/slack/interactions`

## 2) How to connect it to Jira Cloud

### Step A: Create a Jira API token

1. Sign in to Atlassian account settings.
2. Create an API token.
3. Set it in `API_TOKEN`.

Authentication used by the bot is Jira Cloud Basic Auth with:

- Username: `EMAIL`
- Password: `API_TOKEN`

### Step B: Set Jira base URL and account email

- Set `BASE_URL` to your Jira Cloud site URL (for example `https://your-domain.atlassian.net`).
- Set `EMAIL` to the Jira account email that owns the API token.

### Step C: Find and set the progress custom field id

The bot writes progress using the configured Jira field id (`FIELD_ID`).

- The value must be the Jira field key (for example `customfield_12345`).
- The field should be a numeric field compatible with values `0..100`.

### Step D: Validate with a Slack command

From Slack, run:

```text
/progress ABC-123 35
```

The bot will:

- update the configured Jira field to `35`
- add a comment to the issue with Slack user and progress details

You can also open the modal by running:

```text
/progress
```
