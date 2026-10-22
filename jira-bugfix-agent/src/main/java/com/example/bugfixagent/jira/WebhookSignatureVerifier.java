package com.example.bugfixagent.jira;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final SecretKeySpec key;

    public WebhookSignatureVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook secret must not be blank");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public boolean isValid(byte[] body, String signatureHeader) {
        if (body == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()).trim());
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }
        return MessageDigest.isEqual(sign(body), provided);
    }

    byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
