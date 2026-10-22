package com.example.bugfixagent.processing;

import com.example.bugfixagent.jira.JiraIssue;
import java.util.List;

public final class ModeResolver {

    private final FixMode defaultMode;
    private final List<String> analyzeOnlyLabels;
    private final List<String> autoPrLabels;
    private final List<String> autoPrPriorities;

    public ModeResolver(FixMode defaultMode, List<String> analyzeOnlyLabels,
                        List<String> autoPrLabels, List<String> autoPrPriorities) {
        this.defaultMode = defaultMode;
        this.analyzeOnlyLabels = List.copyOf(analyzeOnlyLabels);
        this.autoPrLabels = List.copyOf(autoPrLabels);
        this.autoPrPriorities = List.copyOf(autoPrPriorities);
    }

    public FixMode resolve(JiraIssue issue) {
        if (analyzeOnlyLabels.stream().anyMatch(issue::hasLabel)) {
            return FixMode.ANALYZE_ONLY;
        }
        if (autoPrLabels.stream().anyMatch(issue::hasLabel)) {
            return FixMode.AUTO_PR;
        }
        if (autoPrPriorities.stream().anyMatch(p -> p.equalsIgnoreCase(issue.priority()))) {
            return FixMode.AUTO_PR;
        }
        return defaultMode;
    }
}
