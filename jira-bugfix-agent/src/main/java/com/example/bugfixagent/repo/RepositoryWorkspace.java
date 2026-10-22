package com.example.bugfixagent.repo;

import com.example.bugfixagent.config.AgentProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
public class RepositoryWorkspace {

    private static final DateTimeFormatter BRANCH_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AgentProperties.GitHub github;
    private final Path workDir;
    private final CredentialsProvider credentials;
    private final int cloneDepth;

    public RepositoryWorkspace(AgentProperties properties) {
        this.github = properties.github();
        this.workDir = Paths.get(properties.repo().workDir()).toAbsolutePath().normalize();
        this.cloneDepth = properties.repo().cloneDepth();
        this.credentials = new UsernamePasswordCredentialsProvider("x-access-token", github.token());
    }

    public Workspace checkout(String issueKey) {
        Path dir = workDir.resolve(issueKey);
        try {
            FileSystemUtils.deleteRecursively(dir);
            Files.createDirectories(dir);
            var clone = Git.cloneRepository()
                    .setURI(github.cloneUrl())
                    .setDirectory(dir.toFile())
                    .setBranch(github.baseBranch())
                    .setCredentialsProvider(credentials);
            if (cloneDepth > 0) {
                clone.setDepth(cloneDepth);
            }
            Git git = clone.call();
            return new Workspace(git, dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare work dir " + dir, e);
        } catch (GitAPIException e) {
            throw new GitOperationException("Clone failed for " + github.cloneUrl(), e);
        }
    }

    public String newBranchName(String issueKey) {
        return github.branchPrefix() + issueKey + "-" + LocalDateTime.now().format(BRANCH_STAMP);
    }

    public void createBranch(Workspace workspace, String branch) {
        try {
            workspace.git().checkout().setCreateBranch(true).setName(branch).call();
        } catch (GitAPIException e) {
            throw new GitOperationException("Cannot create branch " + branch, e);
        }
    }

    public void commitAll(Workspace workspace, String message) {
        try {
            Git git = workspace.git();
            git.add().addFilepattern(".").call();
            PersonIdent agent = new PersonIdent("AI Bugfix Agent", "bugfix-agent@users.noreply.github.com");
            git.commit().setMessage(message).setAuthor(agent).setCommitter(agent).call();
        } catch (GitAPIException e) {
            throw new GitOperationException("Commit failed", e);
        }
    }

    public void push(Workspace workspace, String branch) {
        String ref = "refs/heads/" + branch;
        try {
            Iterable<PushResult> results = workspace.git().push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(ref + ":" + ref))
                    .setCredentialsProvider(credentials)
                    .call();
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() != RemoteRefUpdate.Status.OK
                            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw new GitOperationException("Push rejected for %s: %s %s"
                                .formatted(branch, update.getStatus(), update.getMessage()), null);
                    }
                }
            }
        } catch (GitAPIException e) {
            throw new GitOperationException("Push failed for " + branch, e);
        }
    }

    public void discardChanges(Workspace workspace) {
        try {
            workspace.git().reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
            workspace.git().clean().setCleanDirectories(true).setForce(true).call();
        } catch (GitAPIException e) {
            throw new GitOperationException("Cannot discard changes", e);
        }
    }

    public record Workspace(Git git, Path root) implements AutoCloseable {
        @Override
        public void close() {
            git.close();
            try {
                FileSystemUtils.deleteRecursively(root);
            } catch (IOException ignored) {
            }
        }
    }

    public static class GitOperationException extends RuntimeException {
        public GitOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
