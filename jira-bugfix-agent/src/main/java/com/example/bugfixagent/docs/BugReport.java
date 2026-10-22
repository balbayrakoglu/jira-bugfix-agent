package com.example.bugfixagent.docs;

import com.example.bugfixagent.ai.BugAnalysis;
import com.example.bugfixagent.jira.JiraIssue;
import com.example.bugfixagent.processing.FixMode;
import com.example.bugfixagent.repo.BuildVerifier;
import java.time.Instant;
import java.util.List;

public record BugReport(
        JiraIssue issue,
        String issueUrl,
        BugAnalysis analysis,
        FixMode requestedMode,
        FixMode effectiveMode,
        String downgradeReason,
        List<String> changedFiles,
        BuildVerifier.Result verification,
        Instant generatedAt) {

    public BugReport {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }

    public boolean codeChanged() {
        return effectiveMode == FixMode.AUTO_PR && !changedFiles.isEmpty();
    }

    public record Links(String pullRequestUrl, String confluenceUrl, String markdownPath) {
        public static Links none() {
            return new Links(null, null, null);
        }

        public Links withConfluence(String url) {
            return new Links(pullRequestUrl, url, markdownPath);
        }
    }
}
