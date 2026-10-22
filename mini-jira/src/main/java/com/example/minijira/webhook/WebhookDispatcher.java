package com.example.minijira.webhook;

import com.example.minijira.api.JiraJson;
import com.example.minijira.config.MiniJiraProperties;
import com.example.minijira.issue.IssueChangedEvent;
import com.example.minijira.issue.IssueService;
import com.example.minijira.issue.IssueView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int MAX_LOGGED_BODY = 500;

    private final MiniJiraProperties properties;
    private final IssueService issues;
    private final DeliveryLog deliveryLog;
    private final ObjectMapper objectMapper;
    private final WebhookSigner signer;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WebhookDispatcher(MiniJiraProperties properties, IssueService issues, DeliveryLog deliveryLog,
                             ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.issues = issues;
        this.deliveryLog = deliveryLog;
        this.objectMapper = objectMapper;
        this.signer = new WebhookSigner(properties.webhook().secret());
        this.restClient = builder.build();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onIssueChanged(IssueChangedEvent event) {
        MiniJiraProperties.Webhook webhook = properties.webhook();
        if (!webhook.enabled() || !StringUtils.hasText(webhook.url())) {
            return;
        }
        IssueView issue = issues.get(event.issueKey());
        byte[] body = serialize(JiraJson.webhook(event.type().webhookEvent(), event.type().issueEventTypeName(),
                event.actor(), issue, properties.publicUrl(), Instant.now()));
        executor.submit(() -> deliver(webhook, event, body));
    }

    private void deliver(MiniJiraProperties.Webhook webhook, IssueChangedEvent event, byte[] body) {
        Duration backoff = Duration.ofSeconds(2);
        for (int attempt = 1; attempt <= webhook.maxAttempts(); attempt++) {
            try {
                var request = restClient.post()
                        .uri(webhook.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mini-Jira-Webhook/1.0");
                if (signer.hasSecret()) {
                    request = request.header(WebhookSigner.HEADER, signer.sign(body));
                }
                ResponseEntity<String> response = request.body(body).retrieve().toEntity(String.class);
                record(event, attempt, response.getStatusCode().value(), response.getBody(), null);
                return;
            } catch (RestClientResponseException e) {
                record(event, attempt, e.getStatusCode().value(), e.getResponseBodyAsString(), null);
                if (e.getStatusCode().is4xxClientError()) {
                    return;
                }
            } catch (RuntimeException e) {
                record(event, attempt, null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            if (attempt < webhook.maxAttempts()) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        log.warn("Webhook for {} gave up after {} attempts", event.issueKey(), webhook.maxAttempts());
    }

    private void record(IssueChangedEvent event, int attempt, Integer status, String body, String error) {
        String shortBody = body == null || body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "…";
        deliveryLog.record(new DeliveryLog.Delivery(Instant.now(), event.issueKey(), event.type().webhookEvent(),
                attempt, status, shortBody, error));
        log.info("Webhook {} {} attempt {} -> {}", event.type().webhookEvent(), event.issueKey(), attempt,
                status != null ? status : error);
    }

    private byte[] serialize(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize webhook payload", e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
