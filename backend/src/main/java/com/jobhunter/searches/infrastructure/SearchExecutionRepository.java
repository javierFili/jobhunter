package com.jobhunter.searches.infrastructure;

import com.jobhunter.searches.domain.SearchExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SearchExecutionRepository extends JpaRepository<SearchExecution, Long> {

    Page<SearchExecution> findBySavedSearchIdOrderByStartedAtDesc(Long savedSearchId, Pageable pageable);

    Optional<SearchExecution> findByIdAndUserId(Long id, Long userId);

    Optional<SearchExecution> findFirstBySavedSearchIdOrderByStartedAtDesc(Long savedSearchId);

    boolean existsBySavedSearchIdAndStatus(Long savedSearchId, String status);

    /** Runs left RUNNING by a crash/restart. */
    @Modifying
    @Query("update SearchExecution e set e.status = 'FAILED', e.errorSummary = 'Interrumpida (reinicio del servidor)', e.finishedAt = :now where e.status = 'RUNNING' and e.startedAt < :before")
    int failStale(Instant before, Instant now);
}
