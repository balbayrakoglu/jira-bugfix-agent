package com.example.bugfixagent.config;

import com.example.bugfixagent.processing.FixMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        String adminToken,
        @DefaultValue("2") @Positive int maxConcurrentJobs,
        @Valid @NotNull Jira jira,
        @Valid @NotNull Anthropic anthropic,
        @Valid @NotNull GitHub github,
        @Valid @NotNull Confluence confluence,
        @Valid @NotNull Mode mode,
        @Valid @NotNull Repo repo) {

    public record Jira(
            @NotBlank String baseUrl,
            String browseUrl,
            @NotBlank String email,
            @NotBlank String apiToken,
            @NotBlank String webhookSecret,
            @DefaultValue("X-Hub-Signature") String signatureHeader,
            @DefaultValue("ai-bugfix") String triggerLabel,
            @DefaultValue("Bug") List<String> issueTypes,
            @DefaultValue List<String> projectKeys,
            @DefaultValue("true") boolean commentEnabled) {

        public String issueBrowseUrl(String issueKey) {
            String base = browseUrl == null || browseUrl.isBlank() ? baseUrl : browseUrl;
            return base.replaceAll("/+$", "") + "/browse/" + issueKey;
        }
    }

    public record Anthropic(
            @DefaultValue("https://api.anthropic.com") String baseUrl,
            @NotBlank String apiKey,
            @NotBlank String model,
            @DefaultValue("16000") @Positive int maxTokens,
            @DefaultValue("5m") Duration timeout,
            @DefaultValue("English") String outputLanguage) {
    }

    public record GitHub(
            @DefaultValue("https://api.github.com") String apiUrl,
            @NotBlank String token,
            @NotBlank String owner,
            @NotBlank String repo,
            @DefaultValue("main") String baseBranch,
            @DefaultValue("bugfix/") String branchPrefix,
            @DefaultValue List<String> prLabels,
            @DefaultValue("true") boolean draftPr) {

        public String cloneUrl() {
            return "https://github.com/%s/%s.git".formatted(owner, repo);
        }
    }

    public record Confluence(
            @DefaultValue("false") boolean enabled,
            String baseUrl,
            String email,
            String apiToken,
            String spaceId,
            String parentPageId) {
    }

    public record Mode(
            @DefaultValue("ANALYZE_ONLY") FixMode defaultMode,
            @DefaultValue List<String> analyzeOnlyLabels,
            @DefaultValue List<String> autoPrLabels,
            @DefaultValue List<String> autoPrPriorities,
            @DefaultValue("0.6") @DecimalMin("0.0") @DecimalMax("1.0") double minConfidenceForPr,
            @DefaultValue("true") boolean docsPrForAnalysis) {
    }

    public record Repo(
            @DefaultValue("./work") String workDir,
            @DefaultValue("0") int cloneDepth,
            String verifyCommand,
            @DefaultValue("15m") Duration verifyTimeout,
            @DefaultValue("docs/bugfixes") String docsDir,
            @DefaultValue("12") @Positive int maxContextFiles,
            @DefaultValue("180000") @Positive int maxContextChars,
            @DefaultValue("5") @Positive int maxChangedFiles,
            @DefaultValue List<String> protectedPaths) {
    }
}
