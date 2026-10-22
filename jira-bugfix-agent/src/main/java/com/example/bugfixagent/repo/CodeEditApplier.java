package com.example.bugfixagent.repo;

import com.example.bugfixagent.ai.FileEdit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CodeEditApplier {

    private final List<String> protectedPaths;
    private final int maxChangedFiles;

    public CodeEditApplier(List<String> protectedPaths, int maxChangedFiles) {
        this.protectedPaths = List.copyOf(protectedPaths);
        this.maxChangedFiles = maxChangedFiles;
    }

    public List<String> apply(Path repoRoot, List<FileEdit> edits) {
        Path root = realPath(repoRoot);
        Map<String, String> newContents = new LinkedHashMap<>();
        Map<String, Path> targets = new LinkedHashMap<>();

        for (FileEdit edit : edits) {
            String relative = normalize(edit.path());
            Path target = resolveInside(root, relative);
            targets.put(relative, target);

            String current = newContents.containsKey(relative)
                    ? newContents.get(relative)
                    : readIfExists(target);
            newContents.put(relative, applyOne(relative, current, edit));
        }

        if (newContents.size() > maxChangedFiles) {
            throw new EditRejectedException("Fix touches %d files; limit is %d"
                    .formatted(newContents.size(), maxChangedFiles));
        }

        newContents.forEach((relative, content) -> write(targets.get(relative), content));
        return List.copyOf(new ArrayList<>(newContents.keySet()));
    }

    private static String applyOne(String relative, String current, FileEdit edit) {
        if (edit.createsNewFile()) {
            if (current != null) {
                throw new EditRejectedException("Edit for " + relative + " has empty old_text but the file already exists");
            }
            return edit.newText();
        }
        if (current == null) {
            throw new EditRejectedException("File not found: " + relative);
        }
        boolean crlf = current.contains("\r\n");
        String oldText = crlf ? toCrlf(edit.oldText()) : edit.oldText();
        String newText = crlf ? toCrlf(edit.newText()) : edit.newText();

        int first = current.indexOf(oldText);
        if (first < 0) {
            throw new EditRejectedException("old_text not found in " + relative);
        }
        if (current.indexOf(oldText, first + 1) >= 0) {
            throw new EditRejectedException("old_text is not unique in " + relative);
        }
        return current.substring(0, first) + newText + current.substring(first + oldText.length());
    }

    private String normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new EditRejectedException("Edit has no path");
        }
        String path = rawPath.replace('\\', '/').strip();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new EditRejectedException("Absolute paths are not allowed: " + rawPath);
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                throw new EditRejectedException("Path traversal is not allowed: " + rawPath);
            }
        }
        for (String protectedPath : protectedPaths) {
            if (path.startsWith(protectedPath) || path.equals(protectedPath)) {
                throw new EditRejectedException("Path is protected: " + path);
            }
        }
        return path;
    }

    private static Path resolveInside(Path root, String relative) {
        Path target;
        try {
            target = root.resolve(relative).normalize();
        } catch (InvalidPathException e) {
            throw new EditRejectedException("Invalid path: " + relative);
        }
        if (!target.startsWith(root)) {
            throw new EditRejectedException("Path escapes repository: " + relative);
        }
        Path existing = target;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || !realPath(existing).startsWith(root)) {
            throw new EditRejectedException("Path escapes repository via symlink: " + relative);
        }
        if (Files.isSymbolicLink(target)) {
            throw new EditRejectedException("Editing symbolic links is not allowed: " + relative);
        }
        return target;
    }

    private static String readIfExists(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve " + path, e);
        }
    }

    private static String toCrlf(String text) {
        return text.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    public static class EditRejectedException extends RuntimeException {
        public EditRejectedException(String message) {
            super(message);
        }
    }
}
