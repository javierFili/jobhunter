package com.jobhunter.applications.application;

import com.jobhunter.applications.domain.Application;
import com.jobhunter.applications.domain.ApplicationEvent;
import com.jobhunter.applications.domain.ApplicationStatus;
import com.jobhunter.applications.infrastructure.ApplicationEventRepository;
import com.jobhunter.applications.infrastructure.ApplicationRepository;
import com.jobhunter.coverletters.application.CoverLetterService;
import com.jobhunter.coverletters.domain.CoverLetter;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.notifications.application.NotificationService;
import com.jobhunter.resumes.application.ResumeService;
import com.jobhunter.resumes.domain.Resume;
import com.jobhunter.resumes.domain.ResumeVersion;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application pipeline. JobHunter NEVER sends anything: it prepares the package (approved CV PDF, letter, text ready to
 * copy, checklist) and the user records the submission with "mark as submitted".
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final JobQueryService jobs;
    private final ResumeService resumes;
    private final CoverLetterService letters;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, ApplicationEventRepository events, JobQueryService jobs,
                              ResumeService resumes, CoverLetterService letters, NotificationService notifications,
                              AuditService audit, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.jobs = jobs;
        this.resumes = resumes;
        this.letters = letters;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Application createFromJob(long userId, long jobId) {
        if (applications.findFirstByUserIdAndJobOfferId(userId, jobId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_EXISTS", "Ya existe una postulación para esta oferta");
        }
        JobOffer job = jobs.get(jobId);
        var refs = jobs.references(List.of(jobId)).getOrDefault(jobId, List.of());
        var sources = jobs.sourcesById();
        String url = job.getApplyUrl() != null ? job.getApplyUrl() : refs.isEmpty() ? null : refs.get(0).getSourceUrl();
        String source = refs.isEmpty() ? null : Optional.ofNullable(sources.get(refs.get(0).getJobSourceId())).map(s -> s.getDisplayName()).orElse(null);
        Application a = applications.save(new Application(userId, jobId, job.getCompanyName() == null ? "(sin empresa)" : job.getCompanyName(),
                job.getTitle(), url, source));
        event(a, "STATUS_CHANGE", null, a.getStatus(), "Postulación creada desde la oferta", userId);
        return a;
    }

    @Transactional
    public Application createManual(long userId, String company, String position, String jobUrl, String source,
                                    Application.Method method, String notes) {
        if (company == null || company.isBlank() || position == null || position.isBlank()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Empresa y cargo son obligatorios");
        }
        if (jobUrl != null && !jobUrl.isBlank() && !jobUrl.matches("^https?://\\S{1,990}$")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "URL no válida");
        }
        Application a = new Application(userId, null, company.trim(), position.trim(), blank(jobUrl), blank(source));
        a.update(null, null, null, method, null, null, null, null, null, null, null, blank(notes));
        a = applications.save(a);
        event(a, "STATUS_CHANGE", null, a.getStatus(), "Postulación creada manualmente", userId);
        return a;
    }

    @Transactional(readOnly = true)
    public Application get(long userId, long id) {
        return applications.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Postulación"));
    }

    @Transactional(readOnly = true)
    public Optional<Application> byJob(long userId, long jobId) {
        return applications.findFirstByUserIdAndJobOfferId(userId, jobId);
    }

    @Transactional(readOnly = true)
    public Page<Application> list(long userId, List<String> statuses, String q, String sort, int page, int size) {
        Specification<Application> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses.stream().map(s -> ApplicationStatus.valueOf(s.toUpperCase())).toList()));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("companyName")), like, '\\'), cb.like(cb.lower(root.get("positionTitle")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort s = switch (sort == null ? "updated" : sort) {
            case "applied" -> Sort.by(Sort.Order.desc("appliedAt").nullsLast(), Sort.Order.desc("id"));
            case "followUp" -> Sort.by(Sort.Order.asc("followUpAt").nullsLast(), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        };
        return applications.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), s));
    }

    @Transactional(readOnly = true)
    public Map<ApplicationStatus, List<Application>> board(long userId) {
        Map<ApplicationStatus, List<Application>> board = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) board.put(s, new ArrayList<>());
        applications.findByUserIdOrderByKanbanOrderAscUpdatedAtDesc(userId).forEach(a -> board.get(a.getStatus()).add(a));
        return board;
    }

    public record UpdateCommand(String companyName, String positionTitle, String jobUrl, Application.Method method,
                                Long resumeId, Integer resumeVersionNo, Long coverLetterId, BigDecimal salaryExpectation,
                                String salaryCurrency, String salaryPeriod, LocalDate followUpAt, String notes, Integer version) {
    }

    @Transactional
    public Application update(long userId, long id, UpdateCommand c) {
        Application a = get(userId, id);
        a.checkVersion(c.version());
        if (c.resumeId() != null) {
            Resume r = resumes.get(userId, c.resumeId());
            if (c.resumeVersionNo() != null) resumes.version(userId, r.getId(), c.resumeVersionNo());
        }
        if (c.coverLetterId() != null) letters.get(userId, c.coverLetterId());
        if (c.jobUrl() != null && !c.jobUrl().isBlank() && !c.jobUrl().matches("^https?://\\S{1,990}$")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "URL no válida");
        }
        if (c.salaryCurrency() != null && !c.salaryCurrency().matches("^[A-Z]{3}$")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Moneda no válida");
        }
        a.update(blank(c.companyName()), blank(c.positionTitle()), c.jobUrl(), c.method(), c.resumeId(), c.resumeVersionNo(),
                c.coverLetterId(), c.salaryExpectation(), c.salaryCurrency(), c.salaryPeriod(), c.followUpAt(), c.notes());
        if (c.resumeId() != null || c.coverLetterId() != null) {
            event(a, "DOCUMENT_ATTACHED", null, null, (c.resumeId() != null ? "CV asociado" : "") +
                    (c.coverLetterId() != null ? (c.resumeId() != null ? " y " : "") + "carta asociada" : ""), userId);
        }
        return a;
    }

    @Transactional
    public Application transition(long userId, long id, ApplicationStatus target, String note) {
        Application a = get(userId, id);
        ApplicationStatus from = a.transition(target);
        if (target == ApplicationStatus.REJECTED || target == ApplicationStatus.WITHDRAWN) a.clearFollowUp();
        event(a, "STATUS_CHANGE", from, target, note, userId);
        return a;
    }

    @Transactional
    public Application markSubmitted(long userId, long id, Application.Method method, Instant submittedAt, String note) {
        if (method == null) throw ApiException.badRequest("VALIDATION_FAILED", "Indica cómo enviaste la postulación");
        Application a = get(userId, id);
        Instant when = submittedAt == null ? clock.instant() : submittedAt;
        if (when.isAfter(clock.instant().plusSeconds(300))) throw ApiException.badRequest("VALIDATION_FAILED", "La fecha no puede ser futura");
        ApplicationStatus from = a.markSubmitted(method, when);
        if (a.getFollowUpAt() == null) a.update(null, null, null, null, null, null, null, null, null, null,
                LocalDate.ofInstant(when, java.time.ZoneOffset.UTC).plusDays(7), null);
        events.save(new ApplicationEvent(a.getId(), "SUBMISSION_CONFIRMED", from.name(), ApplicationStatus.APPLIED.name(),
                note, null, when, userId));
        audit.record(userId, "APPLICATION_SUBMITTED", "APPLICATION", id, Map.of("method", method.name()));
        return a;
    }

    @Transactional
    public void reorder(long userId, ApplicationStatus status, List<Long> orderedIds) {
        Map<Long, Application> byId = new LinkedHashMap<>();
        applications.findByUserIdAndStatus(userId, status).forEach(a -> byId.put(a.getId(), a));
        int i = 0;
        for (Long id : orderedIds) {
            Application a = byId.get(id);
            if (a == null) throw ApiException.badRequest("VALIDATION_FAILED", "La postulación " + id + " no está en esa columna");
            a.setKanbanOrder(i++);
        }
    }

    @Transactional
    public void delete(long userId, long id) {
        get(userId, id).markDeleted(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ApplicationEvent> events(long userId, long id) {
        get(userId, id);
        return events.findByApplicationIdOrderByOccurredAtDescIdDesc(id);
    }

    @Transactional
    public ApplicationEvent addEvent(long userId, long id, String type, String note, Instant occurredAt) {
        Application a = get(userId, id);
        if (!ApplicationEvent.USER_TYPES.contains(type)) throw ApiException.badRequest("VALIDATION_FAILED", "Tipo de evento no válido");
        if (note == null || note.isBlank() || note.length() > 5000) throw ApiException.badRequest("VALIDATION_FAILED", "La nota es obligatoria (máx. 5000)");
        return events.save(new ApplicationEvent(a.getId(), type, null, null, note.trim(), null,
                occurredAt == null ? clock.instant() : occurredAt, userId));
    }

    // ---- prepare package ----

    public record PreparedResume(Long resumeId, int versionNo, String status, String title) {
    }

    public record PreparedLetter(Long id, String format, String subject, String body) {
    }

    public record EmailDraft(String subject, String body) {
    }

    public record ChecklistItem(String key, String label, boolean done) {
    }

    public record Prepared(Long applicationId, String method, String applyUrl, PreparedResume resume, PreparedLetter coverLetter,
                           EmailDraft email, List<ChecklistItem> checklist, List<String> warnings) {
    }

    @Transactional(readOnly = true)
    public Prepared prepare(long userId, long id) {
        Application a = get(userId, id);
        List<String> warnings = new ArrayList<>();
        JobOffer job = null;
        if (a.getJobOfferId() != null) {
            try {
                job = jobs.get(a.getJobOfferId());
            } catch (ApiException ignored) {
                warnings.add("La oferta original ya no está en el catálogo");
            }
        }
        if (job != null) {
            if ("NOT_ELIGIBLE".equals(job.getBoliviaEligibility())) warnings.add("La oferta figura como NO accesible desde Bolivia: " + job.getEligibilityEvidence());
            if ("UNKNOWN".equals(job.getBoliviaEligibility())) warnings.add("No se confirmó que la oferta acepte candidatos desde Bolivia; revisa la descripción");
            if (!"ACTIVE".equals(job.getStatus())) warnings.add("La oferta podría estar cerrada (" + job.getStatus() + ")");
        }

        PreparedResume resume = null;
        if (a.getResumeId() != null) {
            Resume r = resumes.get(userId, a.getResumeId());
            ResumeVersion v = a.getResumeVersionNo() != null ? resumes.version(userId, r.getId(), a.getResumeVersionNo())
                    : resumes.versions(userId, r.getId()).stream().findFirst().orElse(null);
            if (v != null) {
                resume = new PreparedResume(r.getId(), v.getVersionNo(), v.getStatus().name(), r.getTitle());
                if (v.getStatus() != ResumeVersion.Status.APPROVED) warnings.add("La versión del CV seleccionada no está aprobada");
            }
        } else {
            warnings.add("No asociaste ningún CV a esta postulación");
        }

        PreparedLetter letter = null;
        CoverLetter cl = null;
        if (a.getCoverLetterId() != null) {
            cl = letters.get(userId, a.getCoverLetterId());
            letter = new PreparedLetter(cl.getId(), cl.getFormat(), cl.getSubject(), cl.getBody());
            if (!CoverLetterService.suggestions(cl).isEmpty()) warnings.add("La carta tiene sugerencias sin revisar");
        }
        EmailDraft email = email(a, cl);

        String method = a.getMethod() == null ? null : a.getMethod().name();
        List<ChecklistItem> checklist = new ArrayList<>();
        checklist.add(new ChecklistItem("resume", "CV en PDF aprobado y adjunto", resume != null && "APPROVED".equals(resume.status())));
        checklist.add(new ChecklistItem("eligibility", "Confirmé que puedo postular desde mi país", job != null && "ELIGIBLE".equals(job.getBoliviaEligibility())));
        switch (method == null ? "OTHER" : method) {
            case "EMAIL" -> {
                checklist.add(new ChecklistItem("letter", "Texto del correo revisado", cl != null && "APPROVED".equals(cl.getStatus())));
                checklist.add(new ChecklistItem("recipient", "Destinatario correcto (indicado en la oferta)", false));
                checklist.add(new ChecklistItem("subject", "Asunto con el nombre del cargo", false));
            }
            case "LINKEDIN" -> {
                checklist.add(new ChecklistItem("profile", "Perfil de LinkedIn actualizado y coherente con el CV", false));
                checklist.add(new ChecklistItem("message", "Mensaje corto revisado", cl != null && "LINKEDIN_MESSAGE".equals(cl.getFormat())));
            }
            case "FREELANCE_PLATFORM" -> {
                checklist.add(new ChecklistItem("proposal", "Propuesta freelance revisada", cl != null && "FREELANCE_PROPOSAL".equals(cl.getFormat())));
                checklist.add(new ChecklistItem("portfolio", "Enlaces de portafolio/GitHub listos", false));
            }
            default -> {
                checklist.add(new ChecklistItem("form", "Formulario abierto desde el enlace original", false));
                checklist.add(new ChecklistItem("answers", "Respuestas del formulario revisadas", false));
            }
        }
        return new Prepared(a.getId(), method, a.getJobUrl(), resume, letter, email, checklist, warnings);
    }

    /** Deterministic e-mail text (no AI): the letter when it fits, otherwise a short neutral template. */
    static EmailDraft email(Application a, CoverLetter cl) {
        String lang = cl == null ? "es" : cl.getLanguage();
        String subjectDefault = switch (lang) {
            case "en" -> "Application: " + a.getPositionTitle();
            case "it" -> "Candidatura: " + a.getPositionTitle();
            case "de" -> "Bewerbung: " + a.getPositionTitle();
            case "fr" -> "Candidature : " + a.getPositionTitle();
            default -> "Postulación: " + a.getPositionTitle();
        };
        if (cl != null && ("EMAIL_INTRO".equals(cl.getFormat()) || "FORMAL_LETTER".equals(cl.getFormat()) || "SHORT_MESSAGE".equals(cl.getFormat()))) {
            return new EmailDraft(cl.getSubject() != null ? cl.getSubject() : subjectDefault, cl.getBody());
        }
        String body = switch (lang) {
            case "en" -> "Hello,\n\nI would like to apply for the " + a.getPositionTitle() + " position at " + a.getCompanyName()
                    + ". Please find my CV attached.\n\nKind regards,";
            default -> "Hola:\n\nMe gustaría postular al puesto de " + a.getPositionTitle() + " en " + a.getCompanyName()
                    + ". Adjunto mi CV.\n\nSaludos cordiales,";
        };
        return new EmailDraft(subjectDefault, body);
    }

    /** Daily reminders for due follow-ups (one notification per application and day). */
    @Transactional
    public int notifyFollowUps(LocalDate today) {
        int n = 0;
        for (Application a : applications.dueFollowUps(today)) {
            notifications.notify(a.getUserId(), "FOLLOW_UP", "Seguimiento: " + a.getPositionTitle() + " en " + a.getCompanyName(),
                    "Hoy toca hacer seguimiento de esta postulación (" + a.getStatus() + ").", "/applications/" + a.getId(),
                    Map.of("applicationId", a.getId()));
            a.followUpNotified(today);
            n++;
        }
        return n;
    }

    private void event(Application a, String type, ApplicationStatus from, ApplicationStatus to, String note, Long by) {
        events.save(new ApplicationEvent(a.getId(), type, from == null ? null : from.name(), to == null ? null : to.name(),
                note, null, clock.instant(), by));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
