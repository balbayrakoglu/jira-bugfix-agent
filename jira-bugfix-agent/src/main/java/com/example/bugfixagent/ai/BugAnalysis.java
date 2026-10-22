package com.example.bugfixagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BugAnalysis(
        @JsonProperty("summary") String summary,
        @JsonProperty("root_cause") String rootCause,
        @JsonProperty("affected_components") List<String> affectedComponents,
        @JsonProperty("fix_description") String fixDescription,
        @JsonProperty("edits") List<FileEdit> edits,
        @JsonProperty("testing_notes") String testingNotes,
        @JsonProperty("risk_level") RiskLevel riskLevel,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("needs_human_input") boolean needsHumanInput,
        @JsonProperty("open_questions") List<String> openQuestions) {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public BugAnalysis {
        summary = nullToEmpty(summary);
        rootCause = nullToEmpty(rootCause);
        fixDescription = nullToEmpty(fixDescription);
        testingNotes = nullToEmpty(testingNotes);
        affectedComponents = affectedComponents == null ? List.of() : List.copyOf(affectedComponents);
        edits = edits == null ? List.of() : List.copyOf(edits);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        riskLevel = riskLevel == null ? RiskLevel.HIGH : riskLevel;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public boolean hasEdits() {
        return !edits.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
