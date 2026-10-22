package com.example.minijira.issue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    Optional<Issue> findByIssueKey(String issueKey);

    List<Issue> findAllByOrderByUpdatedAtDesc();

    @Query("select coalesce(max(i.number), 0) from Issue i where i.projectKey = :projectKey")
    int maxNumber(@Param("projectKey") String projectKey);
}
