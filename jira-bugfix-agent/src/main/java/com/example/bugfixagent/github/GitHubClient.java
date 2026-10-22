package com.example.bugfixagent.github;

import com.example.bugfixagent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    public record PullRequest(int number, String url) {
    }

    private final RestClient restClient;
    private final AgentProperties.GitHub config;

    public GitHubClient(RestClient.Builder builder, AgentProperties properties) {
        this.config = properties.github();
        this.restClient = builder
                .baseUrl(config.apiUrl())
                .defaultHeader("Authorization", "Bearer " + config.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public PullRequest openPullRequest(String branch, String title, String body) {
        JsonNode response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", config.owner(), config.repo())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", title,
                        "head", branch,
                        "base", config.baseBranch(),
                        "body", body,
                        "draft", config.draftPr()))
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("Empty GitHub response when opening PR for " + branch);
        }
        var pr = new PullRequest(response.path("number").asInt(), response.path("html_url").asText());
        addLabels(pr.number(), config.prLabels());
        return pr;
    }

    private void addLabels(int number, List<String> labels) {
        if (labels.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/issues/{number}/labels", config.owner(), config.repo(), number)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("labels", labels))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not add labels {} to PR #{}: {}", labels, number, e.getMessage());
        }
    }
}
