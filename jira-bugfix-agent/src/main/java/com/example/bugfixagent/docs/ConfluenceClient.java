package com.example.bugfixagent.docs;

import com.example.bugfixagent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class ConfluenceClient {

    private final AgentProperties.Confluence config;
    private final RestClient restClient;

    public ConfluenceClient(RestClient.Builder builder, AgentProperties properties) {
        this.config = properties.confluence();
        if (isEnabled()) {
            this.restClient = builder
                    .baseUrl(config.baseUrl())
                    .defaultHeaders(headers -> headers.setBasicAuth(config.email(), config.apiToken()))
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    public boolean isEnabled() {
        return config.enabled()
                && StringUtils.hasText(config.baseUrl())
                && StringUtils.hasText(config.spaceId())
                && StringUtils.hasText(config.apiToken());
    }

    public Optional<String> createPage(String title, String storageFormatBody) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> request = new HashMap<>();
        request.put("spaceId", config.spaceId());
        request.put("status", "current");
        request.put("title", title);
        request.put("body", Map.of("representation", "storage", "value", storageFormatBody));
        if (StringUtils.hasText(config.parentPageId())) {
            request.put("parentId", config.parentPageId());
        }

        JsonNode response = restClient.post()
                .uri("/wiki/api/v2/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            return Optional.empty();
        }
        String webUi = response.path("_links").path("webui").asText("");
        String base = response.path("_links").path("base").asText(config.baseUrl() + "/wiki");
        return Optional.of(webUi.isEmpty() ? base : base + webUi);
    }
}
