package com.jobhunter.searches.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Link tables: jobs of each execution, and jobs already reported by each saved search (new-job detection). */
@Repository
public class SearchResultStore {

    private final JdbcTemplate jdbc;

    public SearchResultStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Records the offers seen by a search and returns those that are NEW for it. */
    public Set<Long> recordSeen(long savedSearchId, Collection<Long> offerIds, Instant now) {
        if (offerIds.isEmpty()) return Set.of();
        Set<Long> already = new HashSet<>();
        String in = String.join(",", offerIds.stream().map(String::valueOf).toList());
        jdbc.query("SELECT job_offer_id FROM saved_search_seen_jobs WHERE saved_search_id = ? AND job_offer_id IN (" + in + ")",
                rs -> { already.add(rs.getLong(1)); }, savedSearchId);
        Set<Long> fresh = new HashSet<>(offerIds);
        fresh.removeAll(already);
        List<Object[]> rows = fresh.stream().map(id -> new Object[]{savedSearchId, id, Timestamp.from(now)}).toList();
        jdbc.batchUpdate("INSERT IGNORE INTO saved_search_seen_jobs (saved_search_id, job_offer_id, first_seen_at) VALUES (?, ?, ?)", rows);
        return fresh;
    }

    public void recordExecutionJobs(long executionId, Collection<Long> offerIds, Set<Long> fresh) {
        List<Object[]> rows = offerIds.stream().map(id -> new Object[]{executionId, id, fresh.contains(id)}).toList();
        jdbc.batchUpdate("INSERT IGNORE INTO search_execution_jobs (search_execution_id, job_offer_id, is_new) VALUES (?, ?, ?)", rows);
    }

    public List<Long> executionJobs(long executionId, boolean onlyNew, int offset, int limit) {
        return jdbc.queryForList("SELECT job_offer_id FROM search_execution_jobs WHERE search_execution_id = ?"
                + (onlyNew ? " AND is_new = TRUE" : "") + " ORDER BY job_offer_id DESC LIMIT ? OFFSET ?", Long.class,
                executionId, limit, offset);
    }

    public long countExecutionJobs(long executionId, boolean onlyNew) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM search_execution_jobs WHERE search_execution_id = ?"
                + (onlyNew ? " AND is_new = TRUE" : ""), Long.class, executionId);
        return c == null ? 0 : c;
    }
}
