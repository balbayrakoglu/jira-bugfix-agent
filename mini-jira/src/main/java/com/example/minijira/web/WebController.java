package com.example.minijira.web;

import com.example.minijira.config.MiniJiraProperties;
import com.example.minijira.issue.IssueService;
import com.example.minijira.issue.IssueView;
import com.example.minijira.webhook.DeliveryLog;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {

    private static final String UI_USER = "developer";

    private static final DateTimeFormatter COMMENT_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DELIVERY_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public record CommentRow(String author, String time, String html, boolean fromAgent) {
    }

    public record DeliveryRow(String time, DeliveryLog.Delivery delivery) {
    }

    private final IssueService issues;
    private final DeliveryLog deliveryLog;
    private final MiniJiraProperties properties;

    public WebController(IssueService issues, DeliveryLog deliveryLog, MiniJiraProperties properties) {
        this.issues = issues;
        this.deliveryLog = deliveryLog;
        this.properties = properties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("issues", issues.list());
        model.addAttribute("templates", BugTemplates.ALL);
        addCommon(model);
        return "index";
    }

    @GetMapping("/issues/new")
    public String newIssue(@RequestParam(required = false) String template, Model model) {
        BugTemplates.Template t = BugTemplates.byId(template == null ? "" : template)
                .orElse(new BugTemplates.Template("", "", "", "", "", "Medium", List.of("ai-bugfix")));
        model.addAttribute("template", t);
        model.addAttribute("labelsText", String.join(", ", t.labels()));
        model.addAttribute("priorities", IssueService.PRIORITIES);
        addCommon(model);
        return "new";
    }

    @PostMapping("/issues")
    public String create(@RequestParam String summary,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam(defaultValue = "Bug") String issueType,
                         @RequestParam(defaultValue = "Medium") String priority,
                         @RequestParam(defaultValue = "") String labels) {
        IssueView created = issues.create(new IssueService.CreateCommand(
                summary, description, issueType, priority, split(labels), List.of()), UI_USER);
        return "redirect:/browse/" + created.key() + "?watch=1";
    }

    @GetMapping("/browse/{key}")
    public String browse(@PathVariable String key, @RequestParam(defaultValue = "0") int watch, Model model) {
        IssueView issue = issues.get(key);
        model.addAttribute("issue", issue);
        model.addAttribute("descriptionHtml", WikiMarkupRenderer.toHtml(issue.description()));
        model.addAttribute("comments", issue.comments().stream()
                .map(c -> new CommentRow(c.author(), COMMENT_TIME.format(c.createdAt()), WikiMarkupRenderer.toHtml(c.body()),
                        c.author().equals(properties.username())))
                .toList());
        model.addAttribute("statuses", IssueService.STATUSES);
        model.addAttribute("watch", watch == 1);
        addCommon(model);
        return "issue";
    }

    @PostMapping("/browse/{key}/labels")
    public String labels(@PathVariable String key,
                         @RequestParam(defaultValue = "") String add,
                         @RequestParam(defaultValue = "") String remove,
                         RedirectAttributes redirect) {
        issues.changeLabels(key, split(add), split(remove), UI_USER);
        redirect.addAttribute("watch", 1);
        return "redirect:/browse/" + key;
    }

    @PostMapping("/browse/{key}/status")
    public String status(@PathVariable String key, @RequestParam String status) {
        issues.changeStatus(key, status, UI_USER);
        return "redirect:/browse/" + key;
    }

    @PostMapping("/browse/{key}/comments")
    public String comment(@PathVariable String key, @RequestParam String body) {
        issues.addComment(key, UI_USER, body);
        return "redirect:/browse/" + key;
    }

    @GetMapping("/webhooks")
    public String webhooks(Model model) {
        model.addAttribute("deliveries", deliveryLog.recent().stream()
                .map(d -> new DeliveryRow(DELIVERY_TIME.format(d.at()), d))
                .toList());
        addCommon(model);
        return "webhooks";
    }

    @ExceptionHandler(IssueService.IssueNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(IssueService.IssueNotFoundException e, Model model) {
        model.addAttribute("message", e.getMessage());
        addCommon(model);
        return "error-page";
    }

    @ExceptionHandler(IssueService.InvalidIssueException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IssueService.InvalidIssueException e, Model model) {
        model.addAttribute("message", e.getMessage());
        addCommon(model);
        return "error-page";
    }

    private void addCommon(Model model) {
        model.addAttribute("projectKey", properties.projectKey());
        model.addAttribute("projectName", properties.projectName());
        model.addAttribute("webhookUrl", properties.webhook().enabled() ? properties.webhook().url() : null);
    }

    private static List<String> split(String commaSeparated) {
        return Arrays.stream(commaSeparated.split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
    }
}
