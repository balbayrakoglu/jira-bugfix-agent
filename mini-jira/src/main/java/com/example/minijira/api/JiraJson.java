package com.example.minijira.api;

import com.example.minijira.issue.IssueView;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JiraJson {

    private static final DateTimeFormatter JIRA_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneOffset.UTC);

    private JiraJson() {
    }

    public static Map<String, Object> issue(IssueView issue, String baseUrl) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", issue.summary());
        fields.put("description", issue.description());
        fields.put("issuetype", Map.of("name", issue.issueType(), "subtask", false));
        fields.put("project", Map.of("key", issue.projectKey(), "name", issue.projectKey()));
        fields.put("priority", Map.of("name", issue.priority()));
        fields.put("status", Map.of("name", issue.status()));
        fields.put("labels", issue.labels());
        fields.put("components", issue.components().stream().map(c -> Map.of("name", c)).toList());
        fields.put("created", time(issue.createdAt()));
        fields.put("updated", time(issue.updatedAt()));
        List<Map<String, Object>> comments = issue.comments().stream().map(JiraJson::comment).toList();
        fields.put("comment", Map.of(
                "comments", comments,
                "maxResults", comments.size(),
                "total", comments.size(),
                "startAt", 0));

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", String.valueOf(issue.id()));
        json.put("key", issue.key());
        json.put("self", base(baseUrl) + "/rest/api/2/issue/" + issue.id());
        json.put("fields", fields);
        return json;
    }

    public static Map<String, Object> comment(IssueView.CommentView comment) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", String.valueOf(comment.id()));
        json.put("author", user(comment.author()));
        json.put("body", comment.body());
        json.put("created", time(comment.createdAt()));
        json.put("updated", time(comment.createdAt()));
        return json;
    }

    public static Map<String, Object> webhook(String webhookEvent, String issueEventTypeName, String actor,
                                              IssueView issue, String baseUrl, Instant now) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("timestamp", now.toEpochMilli());
        json.put("webhookEvent", webhookEvent);
        json.put("issue_event_type_name", issueEventTypeName);
        json.put("user", user(actor));
        json.put("issue", issue(issue, baseUrl));
        return json;
    }

    public static Map<String, Object> user(String name) {
        return Map.of("name", name, "displayName", name, "active", true);
    }

    public static Map<String, Object> errors(String message) {
        return Map.of("errorMessages", List.of(message), "errors", Map.of());
    }

    static String time(Instant instant) {
        return JIRA_TIME.format(instant);
    }

    private static String base(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }
}
