package com.jobhunter.searches.api;

import com.jobhunter.jobs.api.JobDtos;
import com.jobhunter.jobs.api.JobViewAssembler;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.searches.application.SearchOrchestrator;
import com.jobhunter.searches.application.SearchService;
import com.jobhunter.searches.domain.JobSourceExecution;
import com.jobhunter.searches.domain.SavedSearch;
import com.jobhunter.searches.domain.SearchExecution;
import com.jobhunter.searches.domain.SearchFilters;
import com.jobhunter.searches.application.ExecutionQueryService;
import com.jobhunter.shared.api.PageResponse;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/searches")
public class SearchController {

    private final SearchService searches;
    private final SearchOrchestrator orchestrator;
    private final ExecutionQueryService executions;
    private final JobQueryService jobs;
    private final JobViewAssembler assembler;

    public SearchController(SearchService searches, SearchOrchestrator orchestrator, ExecutionQueryService executions,
                            JobQueryService jobs, JobViewAssembler assembler) {
        this.searches = searches;
        this.orchestrator = orchestrator;
        this.executions = executions;
        this.jobs = jobs;
        this.assembler = assembler;
    }

    public record ExecutionSummaryDto(Long id, String status, int jobsFound, int jobsNew, Instant startedAt, Instant finishedAt) {
    }

    public record SavedSearchDto(Long id, String name, Long profileId, String keywords, SearchFilters filters,
                                 List<String> sourceCodes, String scheduleType, Integer intervalMinutes,
                                 String dailyTime, String timezone, boolean enabled, int maxResultsPerSource,
                                 boolean notifyOnNew, Instant nextRunAt, Instant lastRunAt,
                                 ExecutionSummaryDto lastExecution, int version) {
    }

    public record ExecutionDto(Long id, String trigger, String status, Instant startedAt, Instant finishedAt,
                               int jobsFound, int jobsNew, int jobsDuplicate, BigDecimal costUsd, String errorSummary,
                               List<SourceExecutionDto> sources) {
        static ExecutionDto from(SearchExecution e, List<SourceExecutionDto> sources) {
            return new ExecutionDto(e.getId(), e.getTrigger(), e.getStatus(), e.getStartedAt(), e.getFinishedAt(),
                    e.getJobsFound(), e.getJobsNew(), e.getJobsDuplicate(), e.getCostUsd(), e.getErrorSummary(), sources);
        }
    }

    public record SourceExecutionDto(String sourceCode, String status, int requestCount, int itemsFetched, int itemsNew,
                                     int itemsDuplicate, BigDecimal costUsd, String errorMessage, Instant startedAt,
                                     Instant finishedAt) {
    }

    private SavedSearchDto dto(SavedSearch s) {
        ExecutionSummaryDto last = executions.last(s.getId())
                .map(e -> new ExecutionSummaryDto(e.getId(), e.getStatus(), e.getJobsFound(), e.getJobsNew(), e.getStartedAt(), e.getFinishedAt()))
                .orElse(null);
        return new SavedSearchDto(s.getId(), s.getName(), s.getProfileId(), s.getKeywords(), s.filters(), s.getSourceCodes(),
                s.getScheduleType(), s.getIntervalMinutes(), s.getDailyTime() == null ? null : s.getDailyTime().toString(),
                s.getTimezone(), s.isEnabled(), s.getMaxResultsPerSource(), s.isNotifyOnNew(), s.getNextRunAt(),
                s.getLastRunAt(), last, s.getVersion());
    }

    @GetMapping
    public List<SavedSearchDto> list() {
        return searches.list(CurrentUser.id()).stream().map(this::dto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchDto create(@Valid @RequestBody SearchService.SavedSearchRequest body) {
        return dto(searches.create(CurrentUser.id(), body));
    }

    @GetMapping("/{id}")
    public SavedSearchDto get(@PathVariable long id) {
        return dto(searches.get(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public SavedSearchDto update(@PathVariable long id, @Valid @RequestBody SearchService.SavedSearchRequest body) {
        return dto(searches.update(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        searches.delete(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> run(@PathVariable long id) {
        SearchExecution e = orchestrator.start(CurrentUser.id(), id, "MANUAL");
        return Map.of("executionId", e.getId(), "status", e.getStatus());
    }

    @GetMapping("/{id}/executions")
    public PageResponse<ExecutionDto> executions(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        searches.get(CurrentUser.id(), id);
        return PageResponse.of(executions.history(id, page, size), e -> ExecutionDto.from(e, null));
    }

    @GetMapping("/executions/{executionId}")
    public ExecutionDto execution(@PathVariable long executionId) {
        SearchExecution e = executions.get(CurrentUser.id(), executionId);
        Map<Long, JobSource> sources = jobs.sourcesById();
        List<SourceExecutionDto> perSource = executions.sources(executionId).stream()
                .map(s -> toDto(s, sources)).toList();
        return ExecutionDto.from(e, perSource);
    }

    @GetMapping("/executions/{executionId}/jobs")
    public PageResponse<JobDtos.JobSummaryDto> executionJobs(@PathVariable long executionId,
                                                             @RequestParam(defaultValue = "false") boolean onlyNew,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.id();
        int s = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        ExecutionQueryService.JobPage jp = executions.jobs(userId, executionId, onlyNew, p, s);
        long total = jp.total();
        var offers = jp.offerIds().stream().map(jobs::get).toList();
        return new PageResponse<>(assembler.summaries(userId, offers), p, s, total, (int) Math.ceil(total / (double) s));
    }

    private static SourceExecutionDto toDto(JobSourceExecution s, Map<Long, JobSource> sources) {
        JobSource src = sources.get(s.getJobSourceId());
        return new SourceExecutionDto(src == null ? null : src.getCode(), s.getStatus(), s.getRequestCount(), s.getItemsFetched(),
                s.getItemsNew(), s.getItemsDuplicate(), s.getCostUsd(), s.getErrorMessage(), s.getStartedAt(), s.getFinishedAt());
    }
}
