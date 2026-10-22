package com.example.bugfixagent.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("s3cret");
    private final byte[] body = "{\"webhookEvent\":\"jira:issue_created\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsCorrectSignature() {
        String header = "sha256=" + HexFormat.of().formatHex(verifier.sign(body));

        assertThat(verifier.isValid(body, header)).isTrue();
    }

    @Test
    void rejectsSignatureOfDifferentBody() {
        String header = "sha256=" + HexFormat.of().formatHex(verifier.sign("other".getBytes(StandardCharsets.UTF_8)));

        assertThat(verifier.isValid(body, header)).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "sha1=abcd")).isFalse();
        assertThat(verifier.isValid(body, "sha256=not-hex")).isFalse();
    }

    @Test
    void refusesBlankSecret() {
        assertThatThrownBy(() -> new WebhookSignatureVerifier(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
