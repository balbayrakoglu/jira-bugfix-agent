package com.example.minijira.api;

import com.example.minijira.config.MiniJiraProperties;
import com.example.minijira.issue.IssueService;
import com.example.minijira.issue.IssueView;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/api/2")
public class JiraRestController {

    private final IssueService issues;
    private final MiniJiraProperties properties;

    public JiraRestController(IssueService issues, MiniJiraProperties properties) {
        this.issues = issues;
        this.properties = properties;
    }

    @GetMapping("/myself")
    public Map<String, Object> myself(@RequestAttribute(ApiAuthFilter.USER_ATTRIBUTE) String user) {
        return JiraJson.user(user);
    }

    @GetMapping("/issue/{key}")
    public Map<String, Object> getIssue(@PathVariable String key) {
        return JiraJson.issue(issues.get(key), properties.publicUrl());
    }

    @PostMapping("/issue")
    public ResponseEntity<Map<String, Object>> createIssue(@RequestBody JsonNode body,
                                                           @RequestAttribute(ApiAuthFilter.USER_ATTRIBUTE) String user) {
        JsonNode fields = body.path("fields");
        IssueView created = issues.create(new IssueService.CreateCommand(
                fields.path("summary").asText(null),
                fields.path("description").asText(""),
                fields.path("issuetype").path("name").asText("Bug"),
                fields.path("priority").path("name").asText("Medium"),
                texts(fields.path("labels"), null),
                texts(fields.path("components"), "name")), user);
        String self = properties.publicUrl().replaceAll("/+$", "") + "/rest/api/2/issue/" + created.id();
        return ResponseEntity.created(URI.create(self))
                .body(Map.of("id", String.valueOf(created.id()), "key", created.key(), "self", self));
    }

    @PutMapping("/issue/{key}")
    public ResponseEntity<Void> updateIssue(@PathVariable String key, @RequestBody JsonNode body,
                                            @RequestAttribute(ApiAuthFilter.USER_ATTRIBUTE) String user) {
        List<String> add = new ArrayList<>();
        List<String> remove = new ArrayList<>();
        for (JsonNode op : body.path("update").path("labels")) {
            if (op.has("add")) add.add(op.get("add").asText());
            if (op.has("remove")) remove.add(op.get("remove").asText());
        }
        JsonNode replacement = body.path("fields").path("labels");
        if (replacement.isArray()) {
            Set<String> target = new HashSet<>(texts(replacement, null));
            List<String> current = issues.get(key).labels();
            current.stream().filter(l -> !target.contains(l)).forEach(remove::add);
            target.stream().filter(l -> !current.contains(l)).forEach(add::add);
        }
        issues.changeLabels(key, add, remove, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/issue/{key}/comment")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable String key, @RequestBody JsonNode body,
                                                          @RequestAttribute(ApiAuthFilter.USER_ATTRIBUTE) String user) {
        IssueView.CommentView comment = issues.addComment(key, user, body.path("body").asText(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(JiraJson.comment(comment));
    }

    @ExceptionHandler(IssueService.IssueNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(IssueService.IssueNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(JiraJson.errors(e.getMessage()));
    }

    @ExceptionHandler(IssueService.InvalidIssueException.class)
    ResponseEntity<Map<String, Object>> badRequest(IssueService.InvalidIssueException e) {
        return ResponseEntity.badRequest().body(JiraJson.errors(e.getMessage()));
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(field == null ? n.asText() : n.path(field).asText()));
        return values;
    }
}
