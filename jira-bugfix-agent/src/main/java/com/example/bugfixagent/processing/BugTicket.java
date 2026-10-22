package com.example.bugfixagent.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "bug_ticket")
public class BugTicket {

    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 64)
    private String issueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FixMode requestedMode;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FixMode effectiveMode;

    @Column(length = 512)
    private String pullRequestUrl;

    @Column(length = 512)
    private String confluenceUrl;

    @Column(length = MAX_ERROR_LENGTH)
    private String errorMessage;

    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BugTicket() {
    }

    public static BugTicket queued(String issueKey) {
        BugTicket ticket = new BugTicket();
        ticket.issueKey = issueKey;
        ticket.status = Status.QUEUED;
        ticket.createdAt = Instant.now();
        ticket.updatedAt = ticket.createdAt;
        return ticket;
    }

    public void requeue() {
        status = Status.QUEUED;
        errorMessage = null;
        touch();
    }

    public void startProcessing() {
        status = Status.PROCESSING;
        attempts++;
        touch();
    }

    public void complete(FixMode requested, FixMode effective, String prUrl, String confluenceUrl) {
        status = Status.COMPLETED;
        requestedMode = requested;
        effectiveMode = effective;
        pullRequestUrl = prUrl;
        this.confluenceUrl = confluenceUrl;
        errorMessage = null;
        touch();
    }

    public void fail(String message) {
        status = Status.FAILED;
        errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIssueKey() { return issueKey; }
    public Status getStatus() { return status; }
    public FixMode getRequestedMode() { return requestedMode; }
    public FixMode getEffectiveMode() { return effectiveMode; }
    public String getPullRequestUrl() { return pullRequestUrl; }
    public String getConfluenceUrl() { return confluenceUrl; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
