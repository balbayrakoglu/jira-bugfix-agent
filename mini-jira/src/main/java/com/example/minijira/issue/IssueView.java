package com.example.minijira.issue;

import java.time.Instant;
import java.util.List;

public record IssueView(
        long id,
        String key,
        String projectKey,
        String summary,
        String description,
        String issueType,
        String priority,
        String status,
        List<String> labels,
        List<String> components,
        List<CommentView> comments,
        Instant createdAt,
        Instant updatedAt) {

    public record CommentView(long id, String author, String body, Instant createdAt) {
    }

    public boolean hasLabel(String label) {
        return labels.contains(label);
    }

    static IssueView of(Issue issue) {
        return new IssueView(
                issue.getId(),
                issue.getIssueKey(),
                issue.getProjectKey(),
                issue.getSummary(),
                issue.getDescription() == null ? "" : issue.getDescription(),
                issue.getIssueType(),
                issue.getPriority(),
                issue.getStatus(),
                List.copyOf(issue.getLabels()),
                List.copyOf(issue.getComponents()),
                issue.getComments().stream()
                        .map(c -> new CommentView(c.getId() == null ? 0 : c.getId(), c.getAuthor(), c.getBody(), c.getCreatedAt()))
                        .toList(),
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}
