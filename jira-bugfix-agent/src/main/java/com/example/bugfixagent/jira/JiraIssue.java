package com.example.bugfixagent.jira;

import java.util.List;

public record JiraIssue(
        String key,
        String projectKey,
        String issueType,
        String summary,
        String description,
        String priority,
        List<String> labels,
        List<String> components,
        List<String> recentComments) {

    public boolean hasLabel(String label) {
        return labels.stream().anyMatch(l -> l.equalsIgnoreCase(label));
    }

    public String searchableText() {
        return String.join("\n", summary, description, String.join("\n", recentComments));
    }
}
