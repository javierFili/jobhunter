package com.jobhunter.resumes.application;

import com.jobhunter.profiles.application.ProfileSnapshotService;
import com.jobhunter.resumes.domain.Resume;
import com.jobhunter.resumes.domain.ResumeDiff;
import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeLabels;
import com.jobhunter.resumes.domain.ResumeVersion;
import com.jobhunter.resumes.infrastructure.PdfRenderer;
import com.jobhunter.resumes.infrastructure.ResumeRepository;
import com.jobhunter.resumes.infrastructure.ResumeVersionRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.files.FileStorageService;
import com.jobhunter.shared.files.StoredFile;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class ResumeService {

    private static final int MAX_RESUMES = 100;

    private final ResumeRepository resumes;
    private final ResumeVersionRepository versions;
    private final ProfileSnapshotService snapshots;
    private final ResumeDocumentBuilder builder;
    private final PdfRenderer renderer;
    private final FileStorageService files;
    private final UserService users;
    private final Clock clock;

    public ResumeService(ResumeRepository resumes, ResumeVersionRepository versions, ProfileSnapshotService snapshots,
                         ResumeDocumentBuilder builder, PdfRenderer renderer, FileStorageService files,
                         UserService users, Clock clock) {
        this.resumes = resumes;
        this.versions = versions;
        this.snapshots = snapshots;
        this.builder = builder;
        this.renderer = renderer;
        this.files = files;
        this.users = users;
        this.clock = clock;
    }

    public record Created(Resume resume, ResumeVersion version, int unverifiedItemsSkipped) {
    }

    /** New resume whose first version is built from VERIFIED profile facts only. */
    @Transactional
    public Created createFromProfile(long userId, Long profileId, String title, String language, String templateId) {
        requireLanguage(language);
        requireTemplate(templateId);
        if (resumes.findByUserIdOrderByUpdatedAtDesc(userId).size() >= MAX_RESUMES) {
            throw ApiException.badRequest("LIMIT_REACHED", "Máximo " + MAX_RESUMES + " CVs");
        }
        var sc = snapshots.build(userId, profileId);
        if (sc.snapshot().experiences().isEmpty() && sc.snapshot().projects().isEmpty()
                && sc.snapshot().skills().isEmpty()) {
            throw ApiException.badRequest("PROFILE_EMPTY",
                    "Tu perfil no tiene experiencias, proyectos ni habilidades verificadas para construir un CV");
        }
        ResumeDocument doc = builder.build(users.get(userId).getFullName(), sc.snapshot(), sc.contact(), language);
        Resume resume = resumes.save(new Resume(userId, sc.snapshot().profileId(), title.trim(), language, templateId));
        ResumeVersion v = versions.save(new ResumeVersion(resume.getId(), 1, null, ResumeVersion.Origin.PROFILE, null,
                language, Json.write(doc), null, null, "Generado desde el perfil"));
        resume.setCurrentVersion(v.getId());
        return new Created(resume, v, sc.snapshot().unverifiedItemsSkipped());
    }

    @Transactional(readOnly = true)
    public List<Resume> list(long userId) {
        return resumes.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Resume get(long userId, long id) {
        return resumes.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("CV"));
    }

    @Transactional
    public Resume update(long userId, long id, String title, String templateId, Integer version) {
        Resume r = get(userId, id);
        r.checkVersion(version);
        if (templateId != null) requireTemplate(templateId);
        r.rename(title == null ? null : title.trim(), templateId);
        return r;
    }

    @Transactional
    public void delete(long userId, long id) {
        get(userId, id).markDeleted(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ResumeVersion> versions(long userId, long resumeId) {
        get(userId, resumeId);
        return versions.findByResumeIdOrderByVersionNoDesc(resumeId);
    }

    @Transactional(readOnly = true)
    public ResumeVersion version(long userId, long resumeId, int versionNo) {
        get(userId, resumeId);
        return versions.findByResumeIdAndVersionNo(resumeId, versionNo)
                .orElseThrow(() -> ApiException.notFound("Versión"));
    }

    /** Saves an edit as a NEW version (versions are immutable). Bullets edited by the user are marked USER_EDITED. */
    @Transactional
    public ResumeVersion saveVersion(long userId, long resumeId, ResumeDocument content, Integer baseVersionNo,
                                     String note) {
        Resume resume = get(userId, resumeId);
        ResumeVersion base = baseVersionNo == null ? null : version(userId, resumeId, baseVersionNo);
        int next = versions.maxVersionNo(resumeId) + 1;
        ResumeVersion v = versions.save(new ResumeVersion(resumeId, next, base == null ? null : base.getId(),
                ResumeVersion.Origin.MANUAL, base == null ? null : base.getJobOfferId(), resume.getLanguage(),
                Json.write(content), null, null, note == null ? "Editado por el usuario" : note));
        resume.setCurrentVersion(v.getId());
        return v;
    }

    /** Adds a version produced elsewhere (e.g. AI optimization in Phase 5) — always as DRAFT. */
    @Transactional
    public ResumeVersion addGeneratedVersion(long userId, long resumeId, ResumeVersion.Origin origin, Long jobOfferId,
                                             String language, ResumeDocument content, Long aiAnalysisId,
                                             String validationReport, String note) {
        Resume resume = get(userId, resumeId);
        int next = versions.maxVersionNo(resumeId) + 1;
        ResumeVersion current = resume.getCurrentVersionId() == null ? null
                : versions.findById(resume.getCurrentVersionId()).orElse(null);
        return versions.save(new ResumeVersion(resumeId, next, current == null ? null : current.getId(), origin,
                jobOfferId, language, Json.write(content), aiAnalysisId, validationReport, note));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ResumeVersion> latestOptimized(long userId, long resumeId, long jobOfferId) {
        get(userId, resumeId);
        return versions.findFirstByResumeIdAndJobOfferIdAndOriginOrderByVersionNoDesc(resumeId, jobOfferId, ResumeVersion.Origin.OPTIMIZED);
    }

    /** Approval freezes the version and stores its PDF. Unconfirmed SUGGESTED bullets block approval. */
    @Transactional
    public ResumeVersion approve(long userId, long resumeId, int versionNo) {
        Resume resume = get(userId, resumeId);
        ResumeVersion v = version(userId, resumeId, versionNo);
        if (v.getStatus() == ResumeVersion.Status.APPROVED) return v;
        ResumeDocument doc = document(v);
        long suggested = doc.experience().stream().flatMap(e -> e.bullets() == null ? java.util.stream.Stream.empty() : e.bullets().stream())
                .filter(b -> b.status() == ResumeDocument.BulletStatus.SUGGESTED).count()
                + doc.projects().stream().flatMap(p -> p.bullets() == null ? java.util.stream.Stream.empty() : p.bullets().stream())
                .filter(b -> b.status() == ResumeDocument.BulletStatus.SUGGESTED).count();
        if (suggested > 0) {
            throw ApiException.badRequest("UNCONFIRMED_SUGGESTIONS", "Hay " + suggested
                    + " frases sugeridas sin confirmar. Edítalas o elimínalas y guarda una nueva versión antes de aprobar");
        }
        byte[] pdf = renderer.pdf(resume.getTemplateId(), doc, v.getLanguage());
        StoredFile file = files.store(userId, pdf, fileName(resume, v), "application/pdf", StoredFile.Purpose.RESUME_PDF);
        v.approve(clock.instant(), file.getId());
        resume.setCurrentVersion(v.getId());
        return v;
    }

    public record Pdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public Pdf pdf(long userId, long resumeId, int versionNo, String templateOverride) {
        Resume resume = get(userId, resumeId);
        ResumeVersion v = version(userId, resumeId, versionNo);
        if (templateOverride == null && v.getPdfFileId() != null) {
            StoredFile f = files.get(userId, v.getPdfFileId());
            return new Pdf(f.getOriginalName(), files.read(f));
        }
        String template = templateOverride != null ? templateOverride : resume.getTemplateId();
        requireTemplate(template);
        return new Pdf(fileName(resume, v), renderer.pdf(template, document(v), v.getLanguage()));
    }

    @Transactional(readOnly = true)
    public String html(long userId, long resumeId, int versionNo) {
        Resume resume = get(userId, resumeId);
        ResumeVersion v = version(userId, resumeId, versionNo);
        return renderer.html(resume.getTemplateId(), document(v), v.getLanguage());
    }

    @Transactional(readOnly = true)
    public List<ResumeDiff.Change> compare(long userId, long resumeId, int from, int to) {
        return ResumeDiff.diff(document(version(userId, resumeId, from)), document(version(userId, resumeId, to)));
    }

    public static ResumeDocument document(ResumeVersion v) {
        return Json.read(v.getContent(), ResumeDocument.class);
    }

    private static String fileName(Resume r, ResumeVersion v) {
        return FileStorageService.sanitizeName(r.getTitle() + " v" + v.getVersionNo() + ".pdf");
    }

    private static void requireLanguage(String language) {
        if (!ResumeLabels.LANGUAGES.contains(language)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Idioma no soportado: " + language);
        }
    }

    private static void requireTemplate(String templateId) {
        if (!PdfRenderer.isKnownTemplate(templateId)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Plantilla desconocida: " + templateId);
        }
    }
}
