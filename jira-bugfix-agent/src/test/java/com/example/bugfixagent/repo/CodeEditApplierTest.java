package com.example.bugfixagent.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bugfixagent.ai.FileEdit;
import com.example.bugfixagent.repo.CodeEditApplier.EditRejectedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeEditApplierTest {

    @TempDir
    Path repo;

    private final CodeEditApplier applier = new CodeEditApplier(List.of(".github/", "pom.xml"), 2);
    private Path service;

    @BeforeEach
    void setUp() throws IOException {
        service = repo.resolve("src/main/java/OrderService.java");
        Files.createDirectories(service.getParent());
        Files.writeString(service, """
                class OrderService {
                    int total(Order o) {
                        return o.items().size();
                    }
                }
                """);
    }

    @Test
    void replacesUniqueTextAndReportsChangedFile() throws IOException {
        List<String> changed = applier.apply(repo, List.of(edit("src/main/java/OrderService.java",
                "return o.items().size();", "return o == null ? 0 : o.items().size();")));

        assertThat(changed).containsExactly("src/main/java/OrderService.java");
        assertThat(Files.readString(service)).contains("return o == null ? 0 : o.items().size();");
    }

    @Test
    void createsNewFileWhenOldTextIsEmpty() throws IOException {
        applier.apply(repo, List.of(edit("src/test/java/OrderServiceTest.java", "", "class OrderServiceTest {}")));

        assertThat(Files.readString(repo.resolve("src/test/java/OrderServiceTest.java"))).isEqualTo("class OrderServiceTest {}");
    }

    @Test
    void preservesCrlfLineEndings() throws IOException {
        Files.writeString(service, "a\r\nb\r\nc\r\n");

        applier.apply(repo, List.of(edit("src/main/java/OrderService.java", "a\nb", "a\nB")));

        assertThat(Files.readString(service)).isEqualTo("a\r\nB\r\nc\r\n");
    }

    @Test
    void writesNothingWhenAnyEditIsInvalid() throws IOException {
        String before = Files.readString(service);

        assertThatThrownBy(() -> applier.apply(repo, List.of(
                edit("src/main/java/OrderService.java", "return o.items().size();", "return 1;"),
                edit("src/main/java/OrderService.java", "does not exist", "x"))))
                .isInstanceOf(EditRejectedException.class)
                .hasMessageContaining("not found");
        assertThat(Files.readString(service)).isEqualTo(before);
    }

    @Test
    void rejectsAmbiguousOldText() throws IOException {
        Files.writeString(service, "x();\nx();\n");

        assertThatThrownBy(() -> applier.apply(repo, List.of(edit("src/main/java/OrderService.java", "x();", "y();"))))
                .isInstanceOf(EditRejectedException.class)
                .hasMessageContaining("not unique");
    }

    @Test
    void rejectsPathsOutsideRepositoryAndProtectedPaths() {
        assertThatThrownBy(() -> applier.apply(repo, List.of(edit("../evil.txt", "", "x"))))
                .isInstanceOf(EditRejectedException.class);
        assertThatThrownBy(() -> applier.apply(repo, List.of(edit("/etc/passwd", "", "x"))))
                .isInstanceOf(EditRejectedException.class);
        assertThatThrownBy(() -> applier.apply(repo, List.of(edit(".github/workflows/ci.yml", "", "x"))))
                .isInstanceOf(EditRejectedException.class)
                .hasMessageContaining("protected");
    }

    @Test
    void rejectsFixesTouchingTooManyFiles() {
        assertThatThrownBy(() -> applier.apply(repo, List.of(
                edit("a.txt", "", "a"), edit("b.txt", "", "b"), edit("c.txt", "", "c"))))
                .isInstanceOf(EditRejectedException.class)
                .hasMessageContaining("limit is 2");
        assertThat(repo.resolve("a.txt")).doesNotExist();
    }

    private static FileEdit edit(String path, String oldText, String newText) {
        return new FileEdit(path, oldText, newText, "test");
    }
}
