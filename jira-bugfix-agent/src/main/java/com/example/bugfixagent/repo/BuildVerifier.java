package com.example.bugfixagent.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class BuildVerifier {

    private static final int OUTPUT_TAIL_CHARS = 6_000;

    public record Result(boolean success, boolean skipped, int exitCode, String outputTail) {

        static Result notConfigured() {
            return new Result(true, true, 0, "Verification skipped (agent.repo.verify-command is empty)");
        }
    }

    private final String command;
    private final Duration timeout;

    public BuildVerifier(String command, Duration timeout) {
        this.command = command == null ? "" : command.strip();
        this.timeout = timeout;
    }

    public Result verify(Path repoRoot) {
        if (command.isEmpty()) {
            return Result.notConfigured();
        }
        Path log = null;
        try {
            log = Files.createTempFile("bugfix-verify-", ".log");
            Process process = new ProcessBuilder(shell(command))
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return new Result(false, false, -1, "Timed out after " + timeout + "\n" + tail(log));
            }
            int exit = process.exitValue();
            return new Result(exit == 0, false, exit, tail(log));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot run verify command: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, false, -1, "Interrupted");
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static List<String> shell(String command) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return windows ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);
    }

    private static String tail(Path log) throws IOException {
        String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        return output.length() <= OUTPUT_TAIL_CHARS ? output : output.substring(output.length() - OUTPUT_TAIL_CHARS);
    }
}
