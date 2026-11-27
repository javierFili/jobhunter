package com.jobhunter.resumes.api;

import com.jobhunter.resumes.application.ResumeService;
import com.jobhunter.resumes.application.ResumeUploadService;
import com.jobhunter.resumes.domain.ParsedResume;
import com.jobhunter.resumes.domain.Resume;
import com.jobhunter.resumes.domain.ResumeDiff;
import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeExtraction;
import com.jobhunter.resumes.domain.ResumeVersion;
import com.jobhunter.resumes.infrastructure.PdfRenderer;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeUploadService uploads;
    private final ResumeService resumes;
    private final com.jobhunter.resumes.application.ResumeAiService resumeAi;
    private final com.jobhunter.shared.tasks.AsyncTasks tasks;

    public ResumeController(ResumeUploadService uploads, ResumeService resumes,
                            com.jobhunter.resumes.application.ResumeAiService resumeAi,
                            com.jobhunter.shared.tasks.AsyncTasks tasks) {
        this.uploads = uploads;
        this.resumes = resumes;
        this.resumeAi = resumeAi;
        this.tasks = tasks;
    }

    /** Optional AI structuring (route CV_PARSE). Requires consent AI_PROCESS_CV for the provider. Poll GET below. */
    @PostMapping("/resumes/uploads/{id}/ai-parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public java.util.Map<String, String> aiParse(@PathVariable long id) {
        long userId = CurrentUser.id();
        uploads.get(userId, id);
        String key = "cv-parse:" + userId + ":" + id;
        tasks.submit(key, () -> resumeAi.parseWithAi(userId, id));
        return java.util.Map.of("state", "RUNNING");
    }

    @GetMapping("/resumes/uploads/{id}/ai-parse")
    public java.util.Map<String, Object> aiParseState(@PathVariable long id) {
        long userId = CurrentUser.id();
        uploads.get(userId, id);
        var s = tasks.status("cv-parse:" + userId + ":" + id);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("state", s.map(x -> x.state().name()).orElse("NONE"));
        out.put("code", s.map(x -> x.code()).orElse(null));
        out.put("message", s.map(x -> x.message()).orElse(null));
        return out;
    }

    // ---- DTOs ----

    public record UploadSummaryDto(Long id, String extractor, String parser, String status, Instant createdAt,
                                   Instant importedAt) {
        static UploadSummaryDto from(ResumeExtraction e) {
            return new UploadSummaryDto(e.getId(), e.getExtractor(), e.getParser(), e.getStatus(), e.getCreatedAt(),
                    e.getImportedAt());
        }
    }

    public record UploadDto(Long id, String extractor, String parser, String status, Instant createdAt,
                            Instant importedAt, String rawText, ParsedResume parsed) {
        static UploadDto from(ResumeExtraction e) {
            return new UploadDto(e.getId(), e.getExtractor(), e.getParser(), e.getStatus(), e.getCreatedAt(),
                    e.getImportedAt(), e.getRawText(), Json.read(e.getParsed(), ParsedResume.class));
        }
    }

    public record ResumeDto(Long id, Long profileId, String title, String language, String templateId,
                            Long currentVersionId, Instant updatedAt, int version) {
        static ResumeDto from(Resume r) {
            return new ResumeDto(r.getId(), r.getProfileId(), r.getTitle(), r.getLanguage(), r.getTemplateId(),
                    r.getCurrentVersionId(), r.getUpdatedAt(), r.getVersion());
        }
    }

    public record VersionSummaryDto(Long id, int versionNo, Long parentVersionId, String origin, Long jobOfferId,
                                    String language, String status, Instant createdAt, Instant approvedAt,
                                    boolean hasPdf, String note) {
        static VersionSummaryDto from(ResumeVersion v) {
            return new VersionSummaryDto(v.getId(), v.getVersionNo(), v.getParentVersionId(), v.getOrigin().name(),
                    v.getJobOfferId(), v.getLanguage(), v.getStatus().name(), v.getCreatedAt(), v.getApprovedAt(),
                    v.getPdfFileId() != null, v.getNote());
        }
    }

    public record VersionDto(VersionSummaryDto summary, ResumeDocument content, Object validationReport) {
        static VersionDto from(ResumeVersion v) {
            return new VersionDto(VersionSummaryDto.from(v), ResumeService.document(v),
                    v.getValidationReport() == null ? null : Json.tree(v.getValidationReport()));
        }
    }

    public record CreateResumeRequest(Long profileId, @NotBlank @Size(max = 150) String title,
                                      @NotBlank String language, @NotBlank String templateId) {
    }

    public record CreatedDto(ResumeDto resume, VersionSummaryDto version, int unverifiedItemsSkipped) {
    }

    public record UpdateResumeRequest(@Size(min = 1, max = 150) String title, String templateId, Integer version) {
    }

    public record SaveVersionRequest(@NotNull @Valid ResumeDocument content, Integer baseVersionNo,
                                     @Size(max = 300) String note) {
    }

    // ---- uploads ----

    @PostMapping(value = "/resumes/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadDto upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("INVALID_FILE", "El archivo está vacío");
        return UploadDto.from(uploads.upload(CurrentUser.id(), file.getOriginalFilename(), file.getContentType(),
                file.getBytes()));
    }

    @GetMapping("/resumes/uploads")
    public List<UploadSummaryDto> uploads() {
        return uploads.list(CurrentUser.id()).stream().map(UploadSummaryDto::from).toList();
    }

    @GetMapping("/resumes/uploads/{id}")
    public UploadDto uploadDetail(@PathVariable long id) {
        return UploadDto.from(uploads.get(CurrentUser.id(), id));
    }

    @DeleteMapping("/resumes/uploads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUpload(@PathVariable long id) {
        uploads.delete(CurrentUser.id(), id);
    }

    @PostMapping("/resumes/uploads/{id}/import")
    public ResumeUploadService.ImportResult importItems(@PathVariable long id,
                                                        @Valid @RequestBody ResumeUploadService.ImportRequest body) {
        return uploads.importItems(CurrentUser.id(), id, body);
    }

    // ---- resumes ----

    @GetMapping("/resume-templates")
    public List<PdfRenderer.Template> templates() {
        return PdfRenderer.TEMPLATES;
    }

    @GetMapping("/resumes")
    public List<ResumeDto> list() {
        return resumes.list(CurrentUser.id()).stream().map(ResumeDto::from).toList();
    }

    @PostMapping("/resumes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDto create(@Valid @RequestBody CreateResumeRequest body) {
        var created = resumes.createFromProfile(CurrentUser.id(), body.profileId(), body.title(), body.language(),
                body.templateId());
        return new CreatedDto(ResumeDto.from(created.resume()), VersionSummaryDto.from(created.version()),
                created.unverifiedItemsSkipped());
    }

    @GetMapping("/resumes/{id}")
    public ResumeDto get(@PathVariable long id) {
        return ResumeDto.from(resumes.get(CurrentUser.id(), id));
    }

    @PatchMapping("/resumes/{id}")
    public ResumeDto update(@PathVariable long id, @Valid @RequestBody UpdateResumeRequest body) {
        return ResumeDto.from(resumes.update(CurrentUser.id(), id, body.title(), body.templateId(), body.version()));
    }

    @DeleteMapping("/resumes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        resumes.delete(CurrentUser.id(), id);
    }

    @GetMapping("/resumes/{id}/versions")
    public List<VersionSummaryDto> versions(@PathVariable long id) {
        return resumes.versions(CurrentUser.id(), id).stream().map(VersionSummaryDto::from).toList();
    }

    @GetMapping("/resumes/{id}/versions/{v}")
    public VersionDto version(@PathVariable long id, @PathVariable int v) {
        return VersionDto.from(resumes.version(CurrentUser.id(), id, v));
    }

    @PostMapping("/resumes/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionSummaryDto saveVersion(@PathVariable long id, @Valid @RequestBody SaveVersionRequest body) {
        return VersionSummaryDto.from(resumes.saveVersion(CurrentUser.id(), id, body.content(), body.baseVersionNo(),
                body.note()));
    }

    @PostMapping("/resumes/{id}/versions/{v}/approve")
    public VersionSummaryDto approve(@PathVariable long id, @PathVariable int v) {
        return VersionSummaryDto.from(resumes.approve(CurrentUser.id(), id, v));
    }

    @GetMapping("/resumes/{id}/versions/{v}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id, @PathVariable int v,
                                      @RequestParam(required = false) String template,
                                      @RequestParam(defaultValue = "false") boolean inline) {
        ResumeService.Pdf pdf = resumes.pdf(CurrentUser.id(), id, v, template);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(pdf.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    @GetMapping("/resumes/{id}/compare")
    public List<ResumeDiff.Change> compare(@PathVariable long id, @RequestParam int from, @RequestParam int to) {
        return resumes.compare(CurrentUser.id(), id, from, to);
    }
}
