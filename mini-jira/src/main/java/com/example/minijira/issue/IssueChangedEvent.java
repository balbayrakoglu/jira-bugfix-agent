package com.example.minijira.issue;

public record IssueChangedEvent(String issueKey, Type type, String actor) {

    public enum Type {
        CREATED("jira:issue_created", "issue_created"),
        UPDATED("jira:issue_updated", "issue_generic");

        private final String webhookEvent;
        private final String issueEventTypeName;

        Type(String webhookEvent, String issueEventTypeName) {
            this.webhookEvent = webhookEvent;
            this.issueEventTypeName = issueEventTypeName;
        }

        public String webhookEvent() {
            return webhookEvent;
        }

        public String issueEventTypeName() {
            return issueEventTypeName;
        }
    }
}
