package com.jobhunter.jobs.api;

import com.jobhunter.jobs.application.JobQuery;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.UserJobState;
import com.jobhunter.shared.api.PageResponse;
import com.jobhunter.shared.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobQueryService jobs;
    private final JobViewAssembler assembler;

    public JobController(JobQueryService jobs, JobViewAssembler assembler) {
        this.jobs = jobs;
        this.assembler = assembler;
    }

    @GetMapping
    public PageResponse<JobDtos.JobSummaryDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> skillIds,
            @RequestParam(required = false) List<String> countries,
            @RequestParam(required = false) List<String> regions,
            @RequestParam(required = false) List<String> modalities,
            @RequestParam(required = false) List<String> remoteScopes,
            @RequestParam(required = false) List<String> eligibility,
            @RequestParam(required = false) List<String> contractTypes,
            @RequestParam(required = false) List<String> engagementTypes,
            @RequestParam(required = false) List<String> seniority,
            @RequestParam(required = false) BigDecimal salaryMinUsdMonth,
            @RequestParam(required = false) List<String> languages,
            @RequestParam(required = false) Integer postedWithinDays,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) List<String> sources,
            @RequestParam(required = false) Boolean saved,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.id();
        JobQuery query = new JobQuery(q, skillIds, upper(countries), upper(regions), upper(modalities), upper(remoteScopes),
                upper(eligibility), upper(contractTypes), upper(engagementTypes), upper(seniority), salaryMinUsdMonth,
                languages, postedWithinDays, company == null || company.isBlank() ? List.of() : List.of(company), List.of(),
                sources, saved, includeHidden, status, sort);
        Page<JobOffer> result = jobs.search(userId, query, null, page, size);
        List<JobDtos.JobSummaryDto> content = assembler.summaries(userId, result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public JobDtos.JobDetailDto detail(@PathVariable long id) {
        return assembler.detail(CurrentUser.id(), jobs.get(id));
    }

    public record StateRequest(Boolean saved, Boolean hidden) {
    }

    @PutMapping("/{id}/state")
    public Map<String, Boolean> state(@PathVariable long id, @RequestBody StateRequest body) {
        UserJobState s = jobs.updateState(CurrentUser.id(), id, body.saved(), body.hidden());
        return Map.of("saved", s.isSaved(), "hidden", s.isHidden());
    }

    private static List<String> upper(List<String> values) {
        return values == null ? List.of() : values.stream().map(v -> v.trim().toUpperCase()).filter(v -> !v.isEmpty()).toList();
    }
}
