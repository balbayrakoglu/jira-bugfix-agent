package com.example.bugfixagent.ai;

import com.example.bugfixagent.config.AgentProperties;
import com.example.bugfixagent.jira.JiraIssue;
import com.example.bugfixagent.processing.FixMode;
import com.example.bugfixagent.repo.SourceFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClaudeClient {

    static final String TOOL_NAME = "submit_bug_analysis";
    private static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties.Anthropic config;
    private final JsonNode toolDefinition;
    private final String systemPrompt;

    public ClaudeClient(RestClient.Builder builder, ObjectMapper objectMapper, AgentProperties properties) {
        this.config = properties.anthropic();
        this.objectMapper = objectMapper;
        this.toolDefinition = readJson("claude/bug-analysis-tool.json");
        this.systemPrompt = readText("claude/system-prompt.txt").formatted(config.outputLanguage());

        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build());
        requestFactory.setReadTimeout(config.timeout());
        this.restClient = builder
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", config.apiKey())
                .defaultHeader("anthropic-version", API_VERSION)
                .build();
    }

    public BugAnalysis analyze(JiraIssue issue, List<SourceFile> files, FixMode mode) {
        Map<String, Object> request = Map.of(
                "model", config.model(),
                "max_tokens", config.maxTokens(),
                "system", systemPrompt,
                "tools", List.of(toolDefinition),
                "tool_choice", Map.of("type", "tool", "name", TOOL_NAME),
                "messages", List.of(Map.of("role", "user", "content", PromptBuilder.userMessage(issue, files, mode))));

        JsonNode response = restClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return parseResponse(response);
    }

    BugAnalysis parseResponse(JsonNode response) {
        if (response == null) {
            throw new AiResponseException("Empty response from Claude API");
        }
        if ("max_tokens".equals(response.path("stop_reason").asText())) {
            throw new AiResponseException("Claude response was truncated (max_tokens). Increase agent.anthropic.max-tokens.");
        }
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && TOOL_NAME.equals(block.path("name").asText())) {
                try {
                    return objectMapper.treeToValue(block.path("input"), BugAnalysis.class);
                } catch (JsonProcessingException e) {
                    throw new AiResponseException("Claude returned an analysis that does not match the schema", e);
                }
            }
        }
        throw new AiResponseException("Claude response contained no " + TOOL_NAME + " call");
    }

    private JsonNode readJson(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + classpathLocation, e);
        }
    }

    private static String readText(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + classpathLocation, e);
        }
    }

    public static class AiResponseException extends RuntimeException {
        public AiResponseException(String message) {
            super(message);
        }

        public AiResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
