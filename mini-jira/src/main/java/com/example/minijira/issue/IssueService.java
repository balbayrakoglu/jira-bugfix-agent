package com.example.minijira.issue;

import com.example.minijira.config.MiniJiraProperties;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IssueService {

    public static final Set<String> ISSUE_TYPES = Set.of("Bug", "Task", "Story");
    public static final List<String> PRIORITIES = List.of("Highest", "High", "Medium", "Low", "Lowest");
    public static final List<String> STATUSES = List.of("To Do", "In Progress", "In Review", "Done");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    public record CreateCommand(String summary, String description, String issueType, String priority,
                                Collection<String> labels, Collection<String> components) {
    }

    private final IssueRepository repository;
    private final ApplicationEventPublisher events;
    private final MiniJiraProperties properties;

    public IssueService(IssueRepository repository, ApplicationEventPublisher events, MiniJiraProperties properties) {
        this.repository = repository;
        this.events = events;
        this.properties = properties;
    }

    public IssueView create(CreateCommand command, String actor) {
        if (command.summary() == null || command.summary().isBlank()) {
            throw new InvalidIssueException("summary is required");
        }
        String type = ISSUE_TYPES.contains(command.issueType()) ? command.issueType() : "Bug";
        String priority = PRIORITIES.contains(command.priority()) ? command.priority() : "Medium";
        String projectKey = properties.projectKey();
        int number = repository.maxNumber(projectKey) + 1;

        Issue issue = repository.save(new Issue(projectKey, number, command.summary().strip(),
                command.description(), type, priority, validLabels(command.labels()), cleaned(command.components())));
        IssueView view = IssueView.of(issue);
        events.publishEvent(new IssueChangedEvent(view.key(), IssueChangedEvent.Type.CREATED, actor));
        return view;
    }

    @Transactional(readOnly = true)
    public IssueView get(String issueKey) {
        return IssueView.of(find(issueKey));
    }

    @Transactional(readOnly = true)
    public List<IssueView> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(IssueView::of).toList();
    }

    public IssueView.CommentView addComment(String issueKey, String author, String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidIssueException("comment body is required");
        }
        Comment comment = find(issueKey).addComment(author, body);
        repository.flush();
        return new IssueView.CommentView(comment.getId(), comment.getAuthor(), comment.getBody(), comment.getCreatedAt());
    }

    public IssueView changeLabels(String issueKey, Collection<String> add, Collection<String> remove, String actor) {
        Issue issue = find(issueKey);
        if (issue.changeLabels(validLabels(add), cleaned(remove))) {
            events.publishEvent(new IssueChangedEvent(issueKey, IssueChangedEvent.Type.UPDATED, actor));
        }
        return IssueView.of(issue);
    }

    public IssueView changeStatus(String issueKey, String status, String actor) {
        if (!STATUSES.contains(status)) {
            throw new InvalidIssueException("unknown status: " + status);
        }
        Issue issue = find(issueKey);
        issue.changeStatus(status);
        events.publishEvent(new IssueChangedEvent(issueKey, IssueChangedEvent.Type.UPDATED, actor));
        return IssueView.of(issue);
    }

    private Issue find(String issueKey) {
        return repository.findByIssueKey(issueKey).orElseThrow(() -> new IssueNotFoundException(issueKey));
    }

    private static List<String> validLabels(Collection<String> labels) {
        List<String> cleaned = cleaned(labels);
        for (String label : cleaned) {
            if (!LABEL.matcher(label).matches()) {
                throw new InvalidIssueException("invalid label: " + label);
            }
        }
        return cleaned;
    }

    private static List<String> cleaned(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).distinct().toList();
    }

    public static class IssueNotFoundException extends RuntimeException {
        public IssueNotFoundException(String key) {
            super("Issue does not exist: " + key);
        }
    }

    public static class InvalidIssueException extends RuntimeException {
        public InvalidIssueException(String message) {
            super(message);
        }
    }
}
