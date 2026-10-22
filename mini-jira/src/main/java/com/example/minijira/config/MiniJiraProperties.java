package com.example.minijira.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mini-jira")
public record MiniJiraProperties(
        @NotBlank String username,
        @NotBlank String apiToken,
        @NotBlank String publicUrl,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]+") String projectKey,
        @DefaultValue("Demo Shop") String projectName,
        @Valid @NotNull Webhook webhook) {

    public record Webhook(
            @DefaultValue("true") boolean enabled,
            String url,
            String secret,
            @DefaultValue("4") @Min(1) int maxAttempts) {
    }

    public String browseUrl(String issueKey) {
        return publicUrl.replaceAll("/+$", "") + "/browse/" + issueKey;
    }
}
