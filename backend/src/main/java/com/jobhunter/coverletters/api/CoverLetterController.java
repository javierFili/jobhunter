package com.jobhunter.coverletters.api;

import com.jobhunter.coverletters.application.CoverLetterService;
import com.jobhunter.coverletters.domain.CoverLetter;
import com.jobhunter.coverletters.infrastructure.LetterPdfRenderer;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.profiles.application.ProfessionalProfileService;
import com.jobhunter.shared.api.PageResponse;
import com.jobhunter.shared.files.FileStorageService;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import com.jobhunter.shared.tasks.AsyncTasks;
import com.jobhunter.users.application.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CoverLetterController {

    private final CoverLetterService letters;
    private final JobQueryService jobs;
    private final AsyncTasks tasks;
    private final LetterPdfRenderer pdf;
    private final UserService users;
    private final ProfessionalProfileService profiles;

    public CoverLetterController(CoverLetterService letters, JobQueryService jobs, AsyncTasks tasks, LetterPdfRenderer pdf,
                                 UserService users, ProfessionalProfileService profiles) {
        this.letters = letters;
        this.jobs = jobs;
        this.tasks = tasks;
        this.pdf = pdf;
        this.users = users;
        this.profiles = profiles;
    }

    public record GenerateRequest(@NotBlank String format, @NotBlank String language, String tone, Long profileId) {
    }

    public record EditRequest(String subject, @NotBlank String body) {
    }

    public record SummaryDto(Long id, Long letterGroupId, Long jobOfferId, String jobTitle, String companyName, String format,
                             String language, int versionNo, String status, String subject, String preview, Instant createdAt) {
    }

    public record LetterDto(Long id, Long letterGroupId, Long jobOfferId, String format, String language, String tone,
                            int versionNo, Long parentId, String status, String subject, String body,
                            List<CoverLetterService.Suggestion> suggestions, JsonNode validationReport, String provider,
                            String model, Instant createdAt) {
        static LetterDto from(CoverLetter c) {
            return new LetterDto(c.getId(), c.getLetterGroupId(), c.getJobOfferId(), c.getFormat(), c.getLanguage(), c.getTone(),
                    c.getVersionNo(), c.getParentId(), c.getStatus(), c.getSubject(), c.getBody(), CoverLetterService.suggestions(c),
                    c.getValidationReport() == null ? null : Json.tree(c.getValidationReport()), c.getProvider(), c.getModel(),
                    c.getCreatedAt());
        }
    }

    private SummaryDto summary(CoverLetter c) {
        JobOffer job = null;
        if (c.getJobOfferId() != null) {
            try {
                job = jobs.get(c.getJobOfferId());
            } catch (RuntimeException ignored) {
                // job removed
            }
        }
        String body = c.getBody().replaceAll("\\s+", " ");
        return new SummaryDto(c.getId(), c.getLetterGroupId(), c.getJobOfferId(), job == null ? null : job.getTitle(),
                job == null ? null : job.getCompanyName(), c.getFormat(), c.getLanguage(), c.getVersionNo(), c.getStatus(),
                c.getSubject(), body.length() > 160 ? body.substring(0, 160) + "…" : body, c.getCreatedAt());
    }

    @PostMapping("/jobs/{jobId}/cover-letters")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> generate(@PathVariable long jobId, @Valid @RequestBody GenerateRequest body) {
        long userId = CurrentUser.id();
        jobs.get(jobId);
        String key = "coverletter:" + userId + ":" + jobId;
        tasks.submit(key, () -> letters.generate(userId, jobId, body.format().toUpperCase(), body.language(), body.tone(), body.profileId()));
        return state(key);
    }

    @GetMapping("/jobs/{jobId}/cover-letters/task")
    public Map<String, Object> task(@PathVariable long jobId) {
        return state("coverletter:" + CurrentUser.id() + ":" + jobId);
    }

    @GetMapping("/cover-letters")
    public PageResponse<SummaryDto> list(@RequestParam(required = false) Long jobId, @RequestParam(required = false) String format,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(letters.list(CurrentUser.id(), jobId, format == null ? null : format.toUpperCase(),
                status == null ? null : status.toUpperCase(), page, size), this::summary);
    }

    @GetMapping("/cover-letters/{id}")
    public LetterDto get(@PathVariable long id) {
        return LetterDto.from(letters.get(CurrentUser.id(), id));
    }

    @GetMapping("/cover-letters/{id}/versions")
    public List<SummaryDto> versions(@PathVariable long id) {
        return letters.versions(CurrentUser.id(), id).stream().map(this::summary).toList();
    }

    @PutMapping("/cover-letters/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public LetterDto edit(@PathVariable long id, @Valid @RequestBody EditRequest body) {
        return LetterDto.from(letters.edit(CurrentUser.id(), id, body.subject(), body.body()));
    }

    @PostMapping("/cover-letters/{id}/approve")
    public LetterDto approve(@PathVariable long id) {
        return LetterDto.from(letters.approve(CurrentUser.id(), id));
    }

    @DeleteMapping("/cover-letters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        letters.delete(CurrentUser.id(), id);
    }

    @GetMapping("/cover-letters/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        long userId = CurrentUser.id();
        CoverLetter c = letters.get(userId, id);
        var profile = profiles.active(userId).orElse(null);
        byte[] content = pdf.pdf(c, users.get(userId).getFullName(), profile == null ? null : profile.getContactEmail(),
                profile == null ? null : profile.getPhone());
        String name = FileStorageService.sanitizeName("carta-" + c.getFormat().toLowerCase() + "-v" + c.getVersionNo() + ".pdf");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }

    private Map<String, Object> state(String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        var s = tasks.status(key);
        m.put("state", s.map(x -> x.state().name()).orElse("NONE"));
        m.put("code", s.map(AsyncTasks.Status::code).orElse(null));
        m.put("message", s.map(AsyncTasks.Status::message).orElse(null));
        return m;
    }
}
