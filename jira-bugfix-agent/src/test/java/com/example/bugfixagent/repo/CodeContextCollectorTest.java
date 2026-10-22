package com.example.bugfixagent.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeContextCollectorTest {

    private static final List<String> REPO_FILES = List.of(
            "src/main/java/com/acme/order/OrderService.java",
            "src/main/java/com/acme/order/OrderController.java",
            "src/main/java/com/acme/payment/PaymentClient.java",
            "src/test/java/com/acme/order/OrderServiceTest.java",
            "src/main/resources/application.yml");

    private final CodeContextCollector collector = new CodeContextCollector(10, 100_000);

    @Test
    void ranksStackTraceFramesHighestAndPullsInTheirTests() {
        String issue = """
                NPE when ordering
                java.lang.NullPointerException
                    at com.acme.order.OrderService.total(OrderService.java:42)
                    at com.acme.order.OrderController.create(OrderController.java:17)
                    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:1)
                """;

        Map<String, Integer> scores = collector.score(REPO_FILES, issue);

        assertThat(scores.get("src/main/java/com/acme/order/OrderService.java"))
                .isGreaterThan(scores.get("src/main/java/com/acme/order/OrderController.java"));
        assertThat(scores).containsKey("src/test/java/com/acme/order/OrderServiceTest.java");
        assertThat(scores).doesNotContainKey("src/main/java/com/acme/payment/PaymentClient.java");
    }

    @Test
    void matchesInnerClassFramesToOuterFile() {
        Map<String, Integer> scores = collector.score(REPO_FILES,
                "at com.acme.payment.PaymentClient$Retry.call(PaymentClient.java:9)");

        assertThat(scores).containsKey("src/main/java/com/acme/payment/PaymentClient.java");
    }

    @Test
    void matchesClassNamesAndFilePathsMentionedInText() {
        Map<String, Integer> scores = collector.score(REPO_FILES,
                "PaymentClient times out, see application.yml timeout setting");

        assertThat(scores).containsKeys(
                "src/main/java/com/acme/payment/PaymentClient.java",
                "src/main/resources/application.yml");
    }

    @Test
    void respectsCharacterBudgetAndSkipsBuildOutput(@TempDir Path repo) throws IOException {
        write(repo, "src/main/java/A/OrderService.java", "x".repeat(60));
        write(repo, "src/main/java/A/OrderController.java", "y".repeat(60));
        write(repo, "target/classes/OrderService.java", "compiled copy");

        List<SourceFile> files = new CodeContextCollector(10, 100)
                .collect(repo, "OrderService and OrderController are broken");

        assertThat(files).hasSize(1);
        assertThat(files).extracting(SourceFile::path).noneMatch(p -> p.startsWith("target/"));
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
