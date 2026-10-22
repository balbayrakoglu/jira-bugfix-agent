package com.example.minijira.issue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "issue", uniqueConstraints = @UniqueConstraint(columnNames = {"projectKey", "number"}))
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String issueKey;

    @Column(nullable = false, length = 16)
    private String projectKey;

    private int number;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(length = 50_000)
    private String description;

    @Column(nullable = false, length = 32)
    private String issueType;

    @Column(nullable = false, length = 32)
    private String priority;

    @Column(nullable = false, length = 32)
    private String status;

    @ElementCollection
    @CollectionTable(name = "issue_label", joinColumns = @JoinColumn(name = "issue_id"))
    @Column(name = "label", length = 64)
    private Set<String> labels = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "issue_component", joinColumns = @JoinColumn(name = "issue_id"))
    @Column(name = "component", length = 64)
    private Set<String> components = new LinkedHashSet<>();

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<Comment> comments = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Issue() {
    }

    Issue(String projectKey, int number, String summary, String description, String issueType, String priority,
          Collection<String> labels, Collection<String> components) {
        this.projectKey = projectKey;
        this.number = number;
        this.issueKey = projectKey + "-" + number;
        this.summary = summary;
        this.description = description;
        this.issueType = issueType;
        this.priority = priority;
        this.status = "To Do";
        this.labels.addAll(labels);
        this.components.addAll(components);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    Comment addComment(String author, String body) {
        Comment comment = new Comment(this, author, body);
        comments.add(comment);
        updatedAt = comment.getCreatedAt();
        return comment;
    }

    boolean changeLabels(Collection<String> add, Collection<String> remove) {
        boolean changed = labels.removeAll(remove);
        changed |= labels.addAll(add);
        if (changed) {
            updatedAt = Instant.now();
        }
        return changed;
    }

    void changeStatus(String newStatus) {
        status = newStatus;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIssueKey() { return issueKey; }
    public String getProjectKey() { return projectKey; }
    public int getNumber() { return number; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getIssueType() { return issueType; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public Set<String> getLabels() { return labels; }
    public Set<String> getComponents() { return components; }
    public List<Comment> getComments() { return comments; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
