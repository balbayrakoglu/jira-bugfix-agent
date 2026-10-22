package com.example.bugfixagent.processing;

import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class TicketQueue {

    private static final Logger log = LoggerFactory.getLogger(TicketQueue.class);

    public enum EnqueueResult { QUEUED, ALREADY_KNOWN, BUSY }

    private final BugTicketRepository repository;
    private final TaskExecutor executor;
    private final BugFixPipeline pipeline;

    public TicketQueue(BugTicketRepository repository,
                       @Qualifier("bugfixExecutor") TaskExecutor executor,
                       BugFixPipeline pipeline) {
        this.repository = repository;
        this.executor = executor;
        this.pipeline = pipeline;
    }

    public EnqueueResult enqueueNew(String issueKey) {
        if (repository.findByIssueKey(issueKey).isPresent()) {
            return EnqueueResult.ALREADY_KNOWN;
        }
        try {
            repository.saveAndFlush(BugTicket.queued(issueKey));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return EnqueueResult.ALREADY_KNOWN;
        }
        submit(issueKey);
        return EnqueueResult.QUEUED;
    }

    public EnqueueResult forceRequeue(String issueKey) {
        BugTicket ticket = repository.findByIssueKey(issueKey).orElseGet(() -> BugTicket.queued(issueKey));
        if (ticket.getStatus() == BugTicket.Status.PROCESSING) {
            return EnqueueResult.BUSY;
        }
        ticket.requeue();
        try {
            repository.saveAndFlush(ticket);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException concurrentChange) {
            return EnqueueResult.BUSY;
        }
        submit(issueKey);
        return EnqueueResult.QUEUED;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        for (BugTicket ticket : repository.findByStatusIn(EnumSet.of(BugTicket.Status.QUEUED, BugTicket.Status.PROCESSING))) {
            if (ticket.getStatus() == BugTicket.Status.PROCESSING) {
                ticket.requeue();
                repository.save(ticket);
            }
            log.info("Resuming unfinished ticket {}", ticket.getIssueKey());
            submit(ticket.getIssueKey());
        }
    }

    private void submit(String issueKey) {
        try {
            executor.execute(() -> pipeline.run(issueKey));
        } catch (TaskRejectedException e) {
            log.error("Processing queue is full, rejecting {}", issueKey);
            repository.findByIssueKey(issueKey).ifPresent(ticket -> {
                ticket.fail("Processing queue is full; retry via POST /api/bugs/" + issueKey + "/process");
                repository.save(ticket);
            });
        }
    }
}
