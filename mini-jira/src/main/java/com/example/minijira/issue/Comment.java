package com.example.minijira.issue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "issue_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @Column(nullable = false, length = 64)
    private String author;

    @Column(nullable = false, length = 100_000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected Comment() {
    }

    Comment(Issue issue, String author, String body) {
        this.issue = issue;
        this.author = author;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
