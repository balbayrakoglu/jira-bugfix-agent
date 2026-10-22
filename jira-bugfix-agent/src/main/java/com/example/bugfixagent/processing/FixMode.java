package com.example.bugfixagent.processing;

public enum FixMode {
    ANALYZE_ONLY("Analysis only"),
    AUTO_PR("Automatic PR");

    private final String label;

    FixMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
