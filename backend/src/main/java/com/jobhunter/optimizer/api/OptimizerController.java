package com.jobhunter.optimizer.api;

import com.jobhunter.optimizer.application.ResumeOptimizerService;
import com.jobhunter.resumes.application.ResumeService;
import com.jobhunter.resumes.domain.ResumeVersion;
import com.jobhunter.shared.security.CurrentUser;
import com.jobhunter.shared.tasks.AsyncTasks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/optimize-resume")
public class OptimizerController {

    private final ResumeOptimizerService optimizer;
    private final ResumeService resumes;
    private final AsyncTasks tasks;

    public OptimizerController(ResumeOptimizerService optimizer, ResumeService resumes, AsyncTasks tasks) {
        this.optimizer = optimizer;
        this.resumes = resumes;
        this.tasks = tasks;
    }

    public record OptimizeRequest(@NotNull Long resumeId, @NotBlank String language, Long profileId) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> optimize(@PathVariable long jobId, @Valid @RequestBody OptimizeRequest body) {
        long userId = CurrentUser.id();
        resumes.get(userId, body.resumeId());
        String key = key(userId, jobId, body.resumeId());
        tasks.submit(key, () -> optimizer.optimize(userId, jobId, body.resumeId(), body.language(), body.profileId()));
        return state(key);
    }

    @GetMapping
    public Map<String, Object> status(@PathVariable long jobId, @RequestParam long resumeId) {
        long userId = CurrentUser.id();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task", state(key(userId, jobId, resumeId)));
        out.put("version", resumes.latestOptimized(userId, resumeId, jobId).map(OptimizerController::summary).orElse(null));
        return out;
    }

    static Map<String, Object> summary(ResumeVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("versionNo", v.getVersionNo());
        m.put("parentVersionId", v.getParentVersionId());
        m.put("origin", v.getOrigin().name());
        m.put("jobOfferId", v.getJobOfferId());
        m.put("language", v.getLanguage());
        m.put("status", v.getStatus().name());
        m.put("createdAt", v.getCreatedAt());
        m.put("approvedAt", v.getApprovedAt());
        m.put("hasPdf", v.getPdfFileId() != null);
        m.put("note", v.getNote());
        return m;
    }

    private Map<String, Object> state(String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        var s = tasks.status(key);
        m.put("state", s.map(x -> x.state().name()).orElse("NONE"));
        m.put("code", s.map(AsyncTasks.Status::code).orElse(null));
        m.put("message", s.map(AsyncTasks.Status::message).orElse(null));
        return m;
    }

    private static String key(long userId, long jobId, long resumeId) {
        return "optimize:" + userId + ":" + jobId + ":" + resumeId;
    }
}
