package com.example.bugfixagent.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bugfixagent.jira.JiraIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModeResolverTest {

    private final ModeResolver resolver = new ModeResolver(FixMode.ANALYZE_ONLY,
            List.of("ai-analyze-only"), List.of("ai-auto-fix"), List.of("Highest", "High"));

    @Test
    void usesDefaultWhenNoRuleMatches() {
        assertThat(resolver.resolve(issue("Medium"))).isEqualTo(FixMode.ANALYZE_ONLY);
    }

    @Test
    void autoFixLabelSelectsAutoPr() {
        assertThat(resolver.resolve(issue("Low", "AI-AUTO-FIX"))).isEqualTo(FixMode.AUTO_PR);
    }

    @Test
    void highPrioritySelectsAutoPr() {
        assertThat(resolver.resolve(issue("high"))).isEqualTo(FixMode.AUTO_PR);
    }

    @Test
    void analyzeOnlyLabelWinsOverEverything() {
        assertThat(resolver.resolve(issue("Highest", "ai-auto-fix", "ai-analyze-only"))).isEqualTo(FixMode.ANALYZE_ONLY);
    }

    private static JiraIssue issue(String priority, String... labels) {
        return new JiraIssue("ABC-1", "ABC", "Bug", "summary", "", priority, List.of(labels), List.of(), List.of());
    }
}
