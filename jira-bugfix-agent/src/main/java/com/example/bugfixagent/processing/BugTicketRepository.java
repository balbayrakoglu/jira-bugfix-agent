package com.example.bugfixagent.processing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugTicketRepository extends JpaRepository<BugTicket, Long> {

    Optional<BugTicket> findByIssueKey(String issueKey);

    List<BugTicket> findByStatusIn(Collection<BugTicket.Status> statuses);

    List<BugTicket> findTop50ByOrderByUpdatedAtDesc();
}
