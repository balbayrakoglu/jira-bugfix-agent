package com.example.bugfixagent.processing;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bugs")
public class BugAdminController {

    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    public record TicketView(String issueKey, BugTicket.Status status, FixMode requestedMode, FixMode effectiveMode,
                             String pullRequestUrl, String confluenceUrl, String errorMessage, int attempts,
                             Instant createdAt, Instant updatedAt) {

        static TicketView of(BugTicket t) {
            return new TicketView(t.getIssueKey(), t.getStatus(), t.getRequestedMode(), t.getEffectiveMode(),
                    t.getPullRequestUrl(), t.getConfluenceUrl(), t.getErrorMessage(), t.getAttempts(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    private final BugTicketRepository repository;
    private final TicketQueue queue;

    public BugAdminController(BugTicketRepository repository, TicketQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    @GetMapping
    public List<TicketView> recent() {
        return repository.findTop50ByOrderByUpdatedAtDesc().stream().map(TicketView::of).toList();
    }

    @GetMapping("/{issueKey}")
    public ResponseEntity<TicketView> get(@PathVariable String issueKey) {
        return repository.findByIssueKey(issueKey)
                .map(TicketView::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{issueKey}/process")
    public ResponseEntity<String> process(@PathVariable String issueKey) {
        if (!ISSUE_KEY.matcher(issueKey).matches()) {
            return ResponseEntity.badRequest().body("Invalid issue key");
        }
        TicketQueue.EnqueueResult result = queue.forceRequeue(issueKey);
        HttpStatus status = result == TicketQueue.EnqueueResult.QUEUED ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(result.name());
    }
}
