package com.example.bugfixagent.processing;

import com.example.bugfixagent.ai.BugAnalysis;
import com.example.bugfixagent.ai.ClaudeClient;
import com.example.bugfixagent.config.AgentProperties;
import com.example.bugfixagent.docs.BugReport;
import com.example.bugfixagent.docs.ConfluenceClient;
import com.example.bugfixagent.docs.DocumentationRenderer;
import com.example.bugfixagent.github.GitHubClient;
import com.example.bugfixagent.jira.JiraClient;
import com.example.bugfixagent.jira.JiraIssue;
import com.example.bugfixagent.repo.BuildVerifier;
import com.example.bugfixagent.repo.CodeContextCollector;
import com.example.bugfixagent.repo.CodeEditApplier;
import com.example.bugfixagent.repo.RepositoryWorkspace;
import com.example.bugfixagent.repo.RepositoryWorkspace.Workspace;
import com.example.bugfixagent.repo.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class BugFixPipeline {

    private static final Logger log = LoggerFactory.getLogger(BugFixPipeline.class);
    private static final DateTimeFormatter PAGE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final BugTicketRepository tickets;
    private final JiraClient jira;
    private final ClaudeClient claude;
    private final GitHubClient github;
    private final ConfluenceClient confluence;
    private final RepositoryWorkspace workspaces;
    private final CodeContextCollector contextCollector;
    private final CodeEditApplier editApplier;
    private final BuildVerifier buildVerifier;
    private final ModeResolver modeResolver;
    private final AgentProperties properties;
    private final Clock clock;

    public BugFixPipeline(BugTicketRepository tickets, JiraClient jira, ClaudeClient claude, GitHubClient github,
                          ConfluenceClient confluence, RepositoryWorkspace workspaces,
                          CodeContextCollector contextCollector, CodeEditApplier editApplier,
                          BuildVerifier buildVerifier, ModeResolver modeResolver, AgentProperties properties) {
        this.tickets = tickets;
        this.jira = jira;
        this.claude = claude;
        this.github = github;
        this.confluence = confluence;
        this.workspaces = workspaces;
        this.contextCollector = contextCollector;
        this.editApplier = editApplier;
        this.buildVerifier = buildVerifier;
        this.modeResolver = modeResolver;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    public void run(String issueKey) {
        BugTicket ticket = tickets.findByIssueKey(issueKey).orElse(null);
        if (ticket == null || ticket.getStatus() != BugTicket.Status.QUEUED) {
            return;
        }
        ticket.startProcessing();
        try {
            ticket = tickets.saveAndFlush(ticket);
        } catch (ObjectOptimisticLockingFailureException alreadyClaimed) {
            return;
        }

        try {
            Outcome outcome = process(issueKey);
            ticket.complete(outcome.report().requestedMode(), outcome.report().effectiveMode(),
                    outcome.links().pullRequestUrl(), outcome.links().confluenceUrl());
            tickets.save(ticket);
            log.info("{} completed: mode={}, pr={}", issueKey, outcome.report().effectiveMode(),
                    outcome.links().pullRequestUrl());
        } catch (Exception e) {
            log.error("{} failed", issueKey, e);
            ticket.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            tickets.save(ticket);
            notifyFailure(issueKey);
        }
    }

    record Outcome(BugReport report, BugReport.Links links) {
    }

    Outcome process(String issueKey) {
        JiraIssue issue = jira.getIssue(issueKey);
        FixMode requestedMode = modeResolver.resolve(issue);
        log.info("{}: processing in {} mode", issueKey, requestedMode);

        try (Workspace workspace = workspaces.checkout(issueKey)) {
            List<SourceFile> context = contextCollector.collect(workspace.root(), issue.searchableText());
            log.info("{}: {} source files selected as context", issueKey, context.size());

            BugAnalysis analysis = claude.analyze(issue, context, requestedMode);

            FixAttempt attempt = requestedMode == FixMode.AUTO_PR
                    ? tryApplyFix(workspace, analysis)
                    : FixAttempt.notAttempted();

            BugReport report = new BugReport(
                    issue,
                    properties.jira().issueBrowseUrl(issueKey),
                    analysis,
                    requestedMode,
                    attempt.downgradeReason() == null ? requestedMode : FixMode.ANALYZE_ONLY,
                    attempt.downgradeReason(),
                    attempt.changedFiles(),
                    attempt.verification(),
                    Instant.now(clock));

            BugReport.Links links = publishToRepository(workspace, report);
            links = links.withConfluence(publishToConfluence(report, links));
            commentOnJira(report, links);
            return new Outcome(report, links);
        }
    }

    record FixAttempt(List<String> changedFiles, BuildVerifier.Result verification, String downgradeReason) {
        static FixAttempt notAttempted() {
            return new FixAttempt(List.of(), null, null);
        }

        static FixAttempt downgraded(String reason) {
            return new FixAttempt(List.of(), null, reason);
        }
    }

    private FixAttempt tryApplyFix(Workspace workspace, BugAnalysis analysis) {
        double minConfidence = properties.mode().minConfidenceForPr();
        if (!analysis.hasEdits()) {
            return FixAttempt.downgraded("The AI did not propose a code change.");
        }
        if (analysis.needsHumanInput()) {
            return FixAttempt.downgraded("The AI reported that it needs more information from a human.");
        }
        if (analysis.confidence() < minConfidence) {
            return FixAttempt.downgraded("Confidence %.0f%% is below the %.0f%% threshold for automatic PRs."
                    .formatted(analysis.confidence() * 100, minConfidence * 100));
        }

        List<String> changed;
        try {
            changed = editApplier.apply(workspace.root(), analysis.edits());
        } catch (CodeEditApplier.EditRejectedException e) {
            workspaces.discardChanges(workspace);
            return FixAttempt.downgraded("The proposed edit could not be applied safely: " + e.getMessage());
        }

        BuildVerifier.Result verification = buildVerifier.verify(workspace.root());
        if (!verification.success()) {
            workspaces.discardChanges(workspace);
            return new FixAttempt(List.of(), verification,
                    "Build/tests failed after applying the fix (exit " + verification.exitCode() + ").\n\n"
                            + "Last output lines:\n\n" + indent(verification.outputTail()));
        }
        return new FixAttempt(changed, verification, null);
    }

    private BugReport.Links publishToRepository(Workspace workspace, BugReport report) {
        boolean openPr = report.codeChanged() || properties.mode().docsPrForAnalysis();
        if (!openPr) {
            return BugReport.Links.none();
        }
        String key = report.issue().key();
        String markdownPath = properties.repo().docsDir().replaceAll("/+$", "") + "/" + key + ".md";

        String branch = workspaces.newBranchName(key);
        workspaces.createBranch(workspace, branch);
        writeFile(workspace.root().resolve(markdownPath), DocumentationRenderer.markdown(report));

        String type = report.codeChanged() ? "fix" : "docs";
        String title = "%s(%s): %s".formatted(type, key, truncate(oneLine(report.issue().summary()), 72));
        workspaces.commitAll(workspace, title + "\n\nGenerated by AI Bugfix Agent from " + report.issueUrl());
        workspaces.push(workspace, branch);

        GitHubClient.PullRequest pr = github.openPullRequest(branch, title,
                DocumentationRenderer.pullRequestBody(report, markdownPath));
        return new BugReport.Links(pr.url(), null, markdownPath);
    }

    private String publishToConfluence(BugReport report, BugReport.Links links) {
        if (!confluence.isEnabled()) {
            return null;
        }
        String title = "%s: %s (%s)".formatted(report.issue().key(),
                truncate(oneLine(report.issue().summary()), 150), PAGE_STAMP.format(report.generatedAt()));
        try {
            return confluence.createPage(title, DocumentationRenderer.confluenceStorage(report, links)).orElse(null);
        } catch (RestClientException e) {
            log.warn("{}: Confluence page could not be created: {}", report.issue().key(), e.getMessage());
            return null;
        }
    }

    private void commentOnJira(BugReport report, BugReport.Links links) {
        if (!properties.jira().commentEnabled()) {
            return;
        }
        try {
            jira.addComment(report.issue().key(), DocumentationRenderer.jiraComment(report, links));
        } catch (RestClientException e) {
            log.warn("{}: Jira comment could not be added: {}", report.issue().key(), e.getMessage());
        }
    }

    private void notifyFailure(String issueKey) {
        if (!properties.jira().commentEnabled()) {
            return;
        }
        try {
            jira.addComment(issueKey, "🤖 AI Bugfix Agent could not process this issue. "
                    + "Details are in the agent logs; a developer can retry it.");
        } catch (RuntimeException e) {
            log.warn("{}: failure comment could not be added: {}", issueKey, e.getMessage());
        }
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String indent(String text) {
        return text.lines().map(line -> "    " + line).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
