package com.example.bugfixagent.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CodeContextCollector {

    private static final Pattern STACK_FRAME =
            Pattern.compile("at\\s+([\\w$]+(?:\\.[\\w$]+)+)\\.[\\w$<>]+\\(([\\w$]+)\\.(?:java|kt):\\d+\\)");
    private static final Pattern FILE_PATH =
            Pattern.compile("([\\w./-]+\\.(?:java|kt|ya?ml|properties|xml|sql))");
    private static final Pattern TYPE_NAME =
            Pattern.compile("\\b([A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+)\\b");

    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("java", "kt", "yml", "yaml", "properties", "xml", "sql");
    private static final Set<String> IGNORED_DIRS =
            Set.of(".git", "target", "build", "node_modules", ".idea", ".gradle", "out", "bin");

    private static final int STACK_FRAME_SCORE = 1_000;
    private static final int FILE_PATH_SCORE = 500;
    private static final int TYPE_NAME_SCORE = 100;

    private final int maxFiles;
    private final int maxChars;

    public CodeContextCollector(int maxFiles, int maxChars) {
        this.maxFiles = maxFiles;
        this.maxChars = maxChars;
    }

    public List<SourceFile> collect(Path repoRoot, String issueText) {
        List<String> repoFiles = listTextFiles(repoRoot);
        Map<String, Integer> scores = score(repoFiles, issueText);

        List<String> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        List<SourceFile> selected = new ArrayList<>();
        int usedChars = 0;
        for (String path : ranked) {
            if (selected.size() >= maxFiles) {
                break;
            }
            String content = readUtf8(repoRoot.resolve(path));
            if (content == null || usedChars + content.length() > maxChars) {
                continue;
            }
            selected.add(new SourceFile(path, content));
            usedChars += content.length();
        }
        return List.copyOf(selected);
    }

    Map<String, Integer> score(List<String> repoFiles, String issueText) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, List<String>> bySimpleName = new HashMap<>();
        for (String file : repoFiles) {
            bySimpleName.computeIfAbsent(simpleName(file), k -> new ArrayList<>()).add(file);
        }

        Matcher frame = STACK_FRAME.matcher(issueText);
        int frameIndex = 0;
        while (frame.find()) {
            String fqcn = frame.group(1);
            int innerClass = fqcn.indexOf('$');
            String outerClass = innerClass >= 0 ? fqcn.substring(0, innerClass) : fqcn;
            String suffix = outerClass.replace('.', '/');
            int frameScore = STACK_FRAME_SCORE - frameIndex++;
            for (String file : repoFiles) {
                String withoutExtension = file.substring(0, file.lastIndexOf('.'));
                if (withoutExtension.endsWith(suffix)) {
                    scores.merge(file, frameScore, Math::max);
                }
            }
        }

        Matcher filePath = FILE_PATH.matcher(issueText);
        while (filePath.find()) {
            String mentioned = filePath.group(1);
            for (String file : repoFiles) {
                if (file.endsWith(mentioned) || mentioned.endsWith(file)) {
                    scores.merge(file, FILE_PATH_SCORE, Math::max);
                }
            }
        }

        Matcher typeName = TYPE_NAME.matcher(issueText);
        while (typeName.find()) {
            for (String file : bySimpleName.getOrDefault(typeName.group(1), List.of())) {
                scores.merge(file, TYPE_NAME_SCORE, Math::max);
            }
        }

        Map<String, Integer> tests = new HashMap<>();
        scores.forEach((file, score) -> {
            if (!isTestFile(file)) {
                for (String candidate : bySimpleName.getOrDefault(simpleName(file) + "Test", List.of())) {
                    tests.merge(candidate, score - 1, Math::max);
                }
            }
        });
        tests.forEach((file, score) -> scores.merge(file, score, Math::max));
        return scores;
    }

    private static List<String> listTextFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isInIgnoredDir(root.relativize(p)))
                    .filter(p -> TEXT_EXTENSIONS.contains(extension(p.getFileName().toString())))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list repository files under " + root, e);
        }
    }

    private static boolean isInIgnoredDir(Path relative) {
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException binaryOrOtherEncoding) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static boolean isTestFile(String path) {
        return path.contains("/test/") || simpleName(path).endsWith("Test");
    }

    static String simpleName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
