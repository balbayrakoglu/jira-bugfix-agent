package com.example.bugfixagent.jira;

import com.example.bugfixagent.config.AgentProperties;
import com.example.bugfixagent.processing.TicketQueue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JiraWebhookController {

    private static final Logger log = LoggerFactory.getLogger(JiraWebhookController.class);
    private static final Set<String> HANDLED_EVENTS = Set.of("jira:issue_created", "jira:issue_updated");
    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    private final WebhookSignatureVerifier signatureVerifier;
    private final AgentProperties.Jira config;
    private final TicketQueue queue;
    private final ObjectMapper objectMapper;

    public JiraWebhookController(AgentProperties properties, TicketQueue queue, ObjectMapper objectMapper) {
        this.config = properties.jira();
        this.signatureVerifier = new WebhookSignatureVerifier(config.webhookSecret());
        this.queue = queue;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/jira")
    public ResponseEntity<String> receive(@RequestBody byte[] body, @RequestHeader HttpHeaders headers) {
        if (!signatureVerifier.isValid(body, headers.getFirst(config.signatureHeader()))) {
            log.warn("Rejected Jira webhook with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Invalid JSON");
        }

        String ignoreReason = ignoreReason(event);
        if (ignoreReason != null) {
            log.debug("Ignoring webhook: {}", ignoreReason);
            return ResponseEntity.ok("ignored: " + ignoreReason);
        }

        String issueKey = event.path("issue").path("key").asText();
        TicketQueue.EnqueueResult result = queue.enqueueNew(issueKey);
        log.info("Webhook for {} -> {}", issueKey, result);
        return ResponseEntity.accepted().body(result.name());
    }

    String ignoreReason(JsonNode event) {
        String type = event.path("webhookEvent").asText();
        if (!HANDLED_EVENTS.contains(type)) {
            return "event " + type;
        }
        JsonNode issue = event.path("issue");
        String key = issue.path("key").asText();
        if (!ISSUE_KEY.matcher(key).matches()) {
            return "missing or invalid issue key";
        }
        JsonNode fields = issue.path("fields");
        String issueType = fields.path("issuetype").path("name").asText();
        if (config.issueTypes().stream().noneMatch(t -> t.equalsIgnoreCase(issueType))) {
            return "issue type " + issueType;
        }
        String project = fields.path("project").path("key").asText();
        if (!config.projectKeys().isEmpty() && config.projectKeys().stream().noneMatch(p -> p.equalsIgnoreCase(project))) {
            return "project " + project;
        }
        boolean hasTriggerLabel = false;
        for (JsonNode label : fields.path("labels")) {
            hasTriggerLabel |= label.asText().equalsIgnoreCase(config.triggerLabel());
        }
        if (!hasTriggerLabel) {
            return "no '" + config.triggerLabel() + "' label";
        }
        return null;
    }
}
