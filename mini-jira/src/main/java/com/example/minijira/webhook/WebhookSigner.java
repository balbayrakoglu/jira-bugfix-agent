package com.example.minijira.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSigner {

    public static final String HEADER = "X-Hub-Signature";

    private final SecretKeySpec key;

    public WebhookSigner(String secret) {
        this.key = secret == null || secret.isBlank()
                ? null
                : new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public boolean hasSecret() {
        return key != null;
    }

    public String sign(byte[] body) {
        if (key == null) {
            throw new IllegalStateException("No webhook secret configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
