package com.jobhunter.matching.api;

import com.jobhunter.ai.domain.AiAnalysis;
import com.jobhunter.jobs.api.JobDtos;
import com.jobhunter.jobs.api.JobViewAssembler;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobRequirement;
import com.jobhunter.matching.application.JobAnalysisService;
import com.jobhunter.matching.application.MatchingService;
import com.jobhunter.matching.domain.JobMatch;
import com.jobhunter.shared.api.PageResponse;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import com.jobhunter.shared.tasks.AsyncTasks;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MatchingController {

    private final MatchingService matching;
    private final JobAnalysisService analysis;
    private final JobQueryService jobs;
    private final JobViewAssembler assembler;
    private final AsyncTasks tasks;

    public MatchingController(MatchingService matching, JobAnalysisService analysis, JobQueryService jobs,
                              JobViewAssembler assembler, AsyncTasks tasks) {
        this.matching = matching;
        this.analysis = analysis;
        this.jobs = jobs;
        this.assembler = assembler;
        this.tasks = tasks;
    }

    public record TaskStateDto(String state, String code, String message) {
        static TaskStateDto of(AsyncTasks tasks, String key, String fallbackState) {
            return tasks.status(key).map(s -> new TaskStateDto(s.state().name(), s.code(), s.message()))
                    .orElse(new TaskStateDto(fallbackState, null, null));
        }
    }

    public record RequirementDto(String kind, String importance, Long skillId, String text, BigDecimal minYears,
                                 String languageCode, String level, String extractedBy) {
        static RequirementDto from(JobRequirement r) {
            return new RequirementDto(r.getKind(), r.getImportance(), r.getSkillId(), r.getText(), r.getMinYears(),
                    r.getLanguageCode(), r.getLevel(), r.getExtractedBy());
        }
    }

    public record AnalysisDto(Long id, String status, String provider, String model, Instant createdAt, BigDecimal costUsd,
                              Integer inputTokens, Integer outputTokens, JsonNode output, String error) {
        static AnalysisDto from(AiAnalysis a) {
            return a == null ? null : new AnalysisDto(a.getId(), a.getStatus().name(), a.getProvider(), a.getModel(),
                    a.getCreatedAt(), a.getCostUsd(), a.getInputTokens(), a.getOutputTokens(),
                    a.getOutput() == null ? null : Json.tree(a.getOutput()), a.getError());
        }
    }

    public record AnalysisStateDto(TaskStateDto task, AnalysisDto analysis, List<RequirementDto> requirements) {
    }

    public record MatchDto(Long jobOfferId, int scoreTotal, String classification, String recommendation, String method,
                           Map<String, Integer> scores, JsonNode reasons, JsonNode strengths, JsonNode gaps,
                           Instant updatedAt) {
        static MatchDto from(JobMatch m) {
            Map<String, Integer> scores = new java.util.LinkedHashMap<>();
            scores.put("skills", m.getScoreSkills());
            scores.put("experience", m.getScoreExperience());
            scores.put("location", m.getScoreLocation());
            scores.put("language", m.getScoreLanguage());
            scores.put("seniority", m.getScoreSeniority());
            scores.put("compensation", m.getScoreCompensation());
            scores.put("contract", m.getScoreContract());
            return new MatchDto(m.getJobOfferId(), m.getScoreTotal(), m.getClassification(), m.getRecommendation(),
                    m.getMethod(), scores, Json.tree(m.getReasons()), Json.tree(m.getStrengths()), Json.tree(m.getGaps()),
                    m.getUpdatedAt());
        }
    }

    public record MatchStateDto(TaskStateDto task, MatchDto match) {
    }

    // ---- job analysis (AI, no personal data) ----

    @PostMapping("/jobs/{id}/analyze")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public TaskStateDto analyze(@PathVariable long id, @RequestParam(defaultValue = "false") boolean force) {
        long userId = CurrentUser.id();
        jobs.get(id);
        String key = "analyze:" + userId + ":" + id;
        tasks.submit(key, () -> analysis.analyze(userId, id, force));
        return TaskStateDto.of(tasks, key, "RUNNING");
    }

    @GetMapping("/jobs/{id}/analysis")
    public AnalysisStateDto analysis(@PathVariable long id) {
        long userId = CurrentUser.id();
        jobs.get(id);
        return new AnalysisStateDto(TaskStateDto.of(tasks, "analyze:" + userId + ":" + id, "NONE"),
                AnalysisDto.from(analysis.latest(id).orElse(null)),
                analysis.requirements(id).stream().map(RequirementDto::from).toList());
    }

    // ---- matching ----

    @PostMapping("/jobs/{id}/match")
    public ResponseEntity<?> match(@PathVariable long id, @RequestParam(required = false) Long profileId,
                                   @RequestParam(defaultValue = "false") boolean ai) {
        long userId = CurrentUser.id();
        jobs.get(id);
        if (!ai) return ResponseEntity.ok(MatchDto.from(matching.match(userId, profileId, id, false)));
        String key = "match:" + userId + ":" + id;
        tasks.submit(key, () -> matching.match(userId, profileId, id, true));
        return ResponseEntity.accepted().body(TaskStateDto.of(tasks, key, "RUNNING"));
    }

    @GetMapping("/jobs/{id}/match")
    public MatchStateDto getMatch(@PathVariable long id, @RequestParam(required = false) Long profileId) {
        long userId = CurrentUser.id();
        return new MatchStateDto(TaskStateDto.of(tasks, "match:" + userId + ":" + id, "NONE"),
                matching.get(userId, profileId, id).map(MatchDto::from).orElse(null));
    }

    @PostMapping("/matches/recompute")
    public Map<String, Integer> recompute(@RequestParam(required = false) Long profileId) {
        return Map.of("computed", matching.recompute(CurrentUser.id(), profileId));
    }

    /** AI analysis + semantic match of the best deterministic candidates (explicit, bounded cost). */
    @PostMapping("/matches/analyze-top")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public TaskStateDto analyzeTop(@RequestParam(defaultValue = "5") int limit, @RequestParam(required = false) Long profileId) {
        long userId = CurrentUser.id();
        int n = Math.max(1, Math.min(20, limit));
        String key = "analyze-top:" + userId;
        tasks.submit(key, () -> {
            Page<JobMatch> top = matching.recommended(userId, profileId, java.util.Set.of("HIGH_MATCH", "MEDIUM_MATCH"), 0, n);
            for (JobMatch m : top.getContent()) {
                if (!"AI_ASSISTED".equals(m.getMethod())) matching.match(userId, profileId, m.getJobOfferId(), true);
            }
        });
        return TaskStateDto.of(tasks, key, "RUNNING");
    }

    @GetMapping("/matches/analyze-top")
    public TaskStateDto analyzeTopState() {
        return TaskStateDto.of(tasks, "analyze-top:" + CurrentUser.id(), "NONE");
    }

    @GetMapping("/jobs/recommended")
    public PageResponse<JobDtos.JobSummaryDto> recommended(@RequestParam(required = false) List<String> classification,
                                                           @RequestParam(required = false) Long profileId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.id();
        Page<JobMatch> result = matching.recommended(userId, profileId,
                classification == null ? null : new HashSet<>(classification.stream().map(String::toUpperCase).toList()), page, size);
        var offers = result.getContent().stream().map(m -> jobs.get(m.getJobOfferId())).toList();
        return new PageResponse<>(assembler.summaries(userId, offers), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
