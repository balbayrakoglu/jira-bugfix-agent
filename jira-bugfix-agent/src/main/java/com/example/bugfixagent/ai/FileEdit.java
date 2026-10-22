package com.example.bugfixagent.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileEdit(
        @JsonProperty("path") String path,
        @JsonProperty("old_text") String oldText,
        @JsonProperty("new_text") String newText,
        @JsonProperty("explanation") String explanation) {

    public FileEdit {
        oldText = oldText == null ? "" : oldText;
        newText = newText == null ? "" : newText;
        explanation = explanation == null ? "" : explanation;
    }

    public boolean createsNewFile() {
        return oldText.isEmpty();
    }
}
