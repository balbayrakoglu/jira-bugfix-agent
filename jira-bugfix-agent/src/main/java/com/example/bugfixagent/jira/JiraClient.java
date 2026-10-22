package com.example.bugfixagent.jira;

import com.example.bugfixagent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JiraClient {

    private static final int MAX_COMMENTS = 10;

    private final RestClient restClient;

    public JiraClient(RestClient.Builder builder, AgentProperties properties) {
        AgentProperties.Jira jira = properties.jira();
        this.restClient = builder
                .baseUrl(jira.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(jira.email(), jira.apiToken()))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public JiraIssue getIssue(String issueKey) {
        JsonNode root = restClient.get()
                .uri("/rest/api/2/issue/{key}?fields=summary,description,priority,labels,components,issuetype,project,comment",
                        issueKey)
                .retrieve()
                .body(JsonNode.class);
        if (root == null) {
            throw new IllegalStateException("Empty Jira response for " + issueKey);
        }
        JsonNode fields = root.path("fields");
        return new JiraIssue(
                root.path("key").asText(issueKey),
                fields.path("project").path("key").asText(""),
                fields.path("issuetype").path("name").asText(""),
                fields.path("summary").asText(""),
                fields.path("description").asText(""),
                fields.path("priority").path("name").asText(""),
                textValues(fields.path("labels"), null),
                textValues(fields.path("components"), "name"),
                lastComments(fields.path("comment").path("comments")));
    }

    public void addComment(String issueKey, String wikiMarkupBody) {
        restClient.post()
                .uri("/rest/api/2/issue/{key}/comment", issueKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", wikiMarkupBody))
                .retrieve()
                .toBodilessEntity();
    }

    private static List<String> textValues(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(field == null ? node.asText() : node.path(field).asText()));
        return List.copyOf(values);
    }

    private static List<String> lastComments(JsonNode comments) {
        List<String> all = new ArrayList<>();
        comments.forEach(c -> all.add(c.path("author").path("displayName").asText("?") + ": " + c.path("body").asText("")));
        return List.copyOf(all.subList(Math.max(0, all.size() - MAX_COMMENTS), all.size()));
    }
}
