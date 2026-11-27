package com.jobhunter.searches.application;

import com.jobhunter.searches.domain.JobSourceExecution;
import com.jobhunter.searches.domain.SearchExecution;
import com.jobhunter.searches.infrastructure.JobSourceExecutionRepository;
import com.jobhunter.searches.infrastructure.SearchExecutionRepository;
import com.jobhunter.searches.infrastructure.SearchResultStore;
import com.jobhunter.shared.api.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Read side of search executions (history, per-source detail, jobs of a run). Always scoped by user. */
@Service
public class ExecutionQueryService {

    private final SearchExecutionRepository executions;
    private final JobSourceExecutionRepository sourceExecutions;
    private final SearchResultStore results;

    public ExecutionQueryService(SearchExecutionRepository executions, JobSourceExecutionRepository sourceExecutions,
                                 SearchResultStore results) {
        this.executions = executions;
        this.sourceExecutions = sourceExecutions;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public Optional<SearchExecution> last(long searchId) {
        return executions.findFirstBySavedSearchIdOrderByStartedAtDesc(searchId);
    }

    @Transactional(readOnly = true)
    public Page<SearchExecution> history(long searchId, int page, int size) {
        return executions.findBySavedSearchIdOrderByStartedAtDesc(searchId,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @Transactional(readOnly = true)
    public SearchExecution get(long userId, long executionId) {
        return executions.findByIdAndUserId(executionId, userId).orElseThrow(() -> ApiException.notFound("Ejecución"));
    }

    @Transactional(readOnly = true)
    public List<JobSourceExecution> sources(long executionId) {
        return sourceExecutions.findBySearchExecutionIdOrderByStartedAtAsc(executionId);
    }

    public record JobPage(List<Long> offerIds, long total) {
    }

    @Transactional(readOnly = true)
    public JobPage jobs(long userId, long executionId, boolean onlyNew, int page, int size) {
        get(userId, executionId);
        return new JobPage(results.executionJobs(executionId, onlyNew, page * size, size),
                results.countExecutionJobs(executionId, onlyNew));
    }
}
