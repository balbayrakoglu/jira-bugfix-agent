package com.example.bugfixagent.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bugfixagent.ai.BugAnalysis;
import com.example.bugfixagent.ai.FileEdit;
import com.example.bugfixagent.jira.JiraIssue;
import com.example.bugfixagent.processing.FixMode;
import com.example.bugfixagent.repo.BuildVerifier;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentationRendererTest {

    private final JiraIssue issue = new JiraIssue("PAY-7", "PAY", "Bug", "Refund <fails>", "desc", "High",
            List.of("ai-bugfix"), List.of(), List.of());

    private final BugAnalysis analysis = new BugAnalysis("Refund fails for zero amounts", "Division by zero in FeeCalculator",
            List.of("FeeCalculator"), "Guard zero amount",
            List.of(new FileEdit("src/main/java/FeeCalculator.java", "a / b", "b == 0 ? 0 : a / b", "guard")),
            "Run FeeCalculatorTest", BugAnalysis.RiskLevel.LOW, 0.85, false, List.of());

    @Test
    void markdownShowsAppliedChangesForAutoPr() {
        BugReport report = report(FixMode.AUTO_PR, null, List.of("src/main/java/FeeCalculator.java"));

        String md = DocumentationRenderer.markdown(report);

        assertThat(md).startsWith("# PAY-7: Refund <fails>")
                .contains("## Root cause", "## Code changes", "```java\nb == 0 ? 0 : a / b\n```", "Passed ✅")
                .doesNotContain("Why the fix was not applied");
    }

    @Test
    void downgradedReportExplainsWhyAndMarksSuggestion() {
        BugReport report = report(FixMode.ANALYZE_ONLY, "Build/tests failed", List.of());

        assertThat(DocumentationRenderer.markdown(report))
                .contains("Suggested code changes (not applied)", "Why the fix was not applied automatically",
                        "Analysis only (requested: Automatic PR)");
        assertThat(DocumentationRenderer.jiraComment(report, BugReport.Links.none()))
                .contains("{code:java}", "*Not applied automatically:* Build/tests failed");
    }

    @Test
    void confluenceOutputEscapesHtmlAndCdata() {
        BugReport report = report(FixMode.AUTO_PR, null, List.of("x"));

        String html = DocumentationRenderer.confluenceStorage(report,
                new BugReport.Links("https://github.com/acme/pay/pull/1", null, "docs/bugfixes/PAY-7.md"));

        assertThat(html).contains("ac:name=\"code\"", "https://github.com/acme/pay/pull/1")
                .doesNotContain("<fails>");
        assertThat(DocumentationRenderer.codeMacro("x]]>y", "java")).contains("x]]]]><![CDATA[>y");
    }

    @Test
    void fenceGrowsWhenCodeContainsBackticks() {
        assertThat(DocumentationRenderer.fenced("```nested```", "md")).startsWith("````md\n");
    }

    private BugReport report(FixMode effective, String downgradeReason, List<String> changed) {
        var verification = new BuildVerifier.Result(downgradeReason == null, false, downgradeReason == null ? 0 : 1, "");
        return new BugReport(issue, "https://acme.atlassian.net/browse/PAY-7", analysis, FixMode.AUTO_PR, effective,
                downgradeReason, changed, verification, Instant.parse("2026-10-20T08:00:00Z"));
    }
}
