package com.example.bugfixagent.ai;

import com.example.bugfixagent.jira.JiraIssue;
import com.example.bugfixagent.processing.FixMode;
import com.example.bugfixagent.repo.SourceFile;
import java.util.List;

final class PromptBuilder {

    private PromptBuilder() {
    }

    static String userMessage(JiraIssue issue, List<SourceFile> files, FixMode mode) {
        var sb = new StringBuilder(8_192);
        sb.append("<jira_issue key=\"").append(issue.key()).append("\">\n")
                .append("Type: ").append(issue.issueType()).append('\n')
                .append("Priority: ").append(issue.priority()).append('\n')
                .append("Components: ").append(String.join(", ", issue.components())).append('\n')
                .append("Summary: ").append(issue.summary()).append("\n\n")
                .append("Description:\n").append(issue.description()).append('\n');
        if (!issue.recentComments().isEmpty()) {
            sb.append("\nRecent comments:\n");
            issue.recentComments().forEach(c -> sb.append("- ").append(c).append('\n'));
        }
        sb.append("</jira_issue>\n\n");

        if (files.isEmpty()) {
            sb.append("<repository_files/>\nNo matching source files were found. Analyse from the description only "
                    + "and return an empty edits list.\n\n");
        } else {
            sb.append("<repository_files>\n");
            for (SourceFile file : files) {
                sb.append("<file path=\"").append(file.path()).append("\">\n")
                        .append(file.content())
                        .append("\n</file>\n");
            }
            sb.append("</repository_files>\n\n");
        }

        sb.append(switch (mode) {
            case AUTO_PR -> "Mode: AUTO_PR. Your edits will be applied automatically, built and tested, "
                    + "then opened as a pull request for human review. Only propose edits you are confident in.";
            case ANALYZE_ONLY -> "Mode: ANALYZE_ONLY. Your edits will NOT be applied; they are shown to developers "
                    + "as a suggested change. Still make them precise and minimal.";
        });
        return sb.toString();
    }
}
