package com.jobhunter.resumes.application;

import com.jobhunter.profiles.application.Commands.CertificationCommand;
import com.jobhunter.profiles.application.Commands.EducationCommand;
import com.jobhunter.profiles.application.Commands.ExperienceCommand;
import com.jobhunter.profiles.application.Commands.LanguageCommand;
import com.jobhunter.profiles.application.Commands.ProjectCommand;
import com.jobhunter.profiles.application.ProfileItemsService;
import com.jobhunter.profiles.application.ProfileService;
import com.jobhunter.profiles.application.SkillDictionary;
import com.jobhunter.profiles.application.SkillService;
import com.jobhunter.profiles.domain.Enums;
import com.jobhunter.profiles.domain.Enums.ItemSource;
import com.jobhunter.resumes.domain.DocumentType;
import com.jobhunter.resumes.domain.HeuristicResumeParser;
import com.jobhunter.resumes.domain.ParsedResume;
import com.jobhunter.resumes.domain.ResumeExtraction;
import com.jobhunter.resumes.infrastructure.ResumeExtractionRepository;
import com.jobhunter.resumes.infrastructure.TextExtractor;
import com.jobhunter.resumes.infrastructure.UploadValidator;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.shared.files.FileStorageService;
import com.jobhunter.shared.files.StoredFile;
import com.jobhunter.shared.json.Json;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Upload → validate → extract text locally → heuristic parse → user review → import as UNVERIFIED profile items. */
@Service
public class ResumeUploadService {

    private final UploadValidator validator;
    private final TextExtractor extractor;
    private final FileStorageService files;
    private final ResumeExtractionRepository extractions;
    private final SkillDictionary dictionary;
    private final ProfileItemsService items;
    private final ProfileService profileService;
    private final SkillService skills;
    private final AuditService audit;
    private final Clock clock;

    public ResumeUploadService(UploadValidator validator, TextExtractor extractor, FileStorageService files,
                               ResumeExtractionRepository extractions, SkillDictionary dictionary,
                               ProfileItemsService items, ProfileService profileService, SkillService skills,
                               AuditService audit, Clock clock) {
        this.validator = validator;
        this.extractor = extractor;
        this.files = files;
        this.extractions = extractions;
        this.dictionary = dictionary;
        this.items = items;
        this.profileService = profileService;
        this.skills = skills;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ResumeExtraction upload(long userId, String filename, String contentType, byte[] content) {
        DocumentType type = validator.validate(filename, contentType, content);
        String text = extractor.extract(type, content);
        StoredFile file = files.store(userId, content, filename, type.mediaType(), StoredFile.Purpose.CV_UPLOAD);
        ParsedResume parsed = parser().parse(text);
        ResumeExtraction extraction = extractions.save(new ResumeExtraction(userId, file.getId(),
                type == DocumentType.PDF ? "PDFBOX" : "POI", "SUCCEEDED", text, Json.write(parsed), "HEURISTIC", null));
        audit.record(userId, "CV_UPLOADED", "RESUME_EXTRACTION", extraction.getId(), Map.of("type", type.name()));
        return extraction;
    }

    HeuristicResumeParser parser() {
        return new HeuristicResumeParser(text -> dictionary.find(text).stream()
                .map(m -> new ParsedResume.SkillItem(m.skillId(), m.name())).toList());
    }

    @Transactional(readOnly = true)
    public List<ResumeExtraction> list(long userId) {
        return extractions.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ResumeExtraction get(long userId, long id) {
        return extractions.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Carga de CV"));
    }

    @Transactional
    public void delete(long userId, long id) {
        ResumeExtraction extraction = get(userId, id);
        StoredFile file = files.get(userId, extraction.getFileId());
        extractions.delete(extraction);
        files.delete(file);
    }

    /** Replaces the parsed structure (e.g. after AI parsing in Phase 4). */
    @Transactional
    public void replaceParsed(long userId, long id, ParsedResume parsed, String parser) {
        get(userId, id).replaceParsed(Json.write(parsed), parser);
    }

    // ---- import (user-reviewed, possibly edited, items) ----

    public record ImportRequest(
            List<@Valid ExperienceCommand> experiences,
            List<@Valid ProjectCommand> projects,
            List<@Valid EducationCommand> educations,
            List<@Valid CertificationCommand> certifications,
            List<@NotNull Long> skillIds,
            List<@Valid LanguageCommand> languages) {
    }

    public record ImportResult(int experiences, int projects, int educations, int certifications, int skills,
                               int languages) {
    }

    /**
     * Imports items into the profile as CV_IMPORT / UNVERIFIED. The user must verify them before they can be used
     * in generated resumes or by the AI. Existing languages are never downgraded or overwritten.
     */
    @Transactional
    public ImportResult importItems(long userId, long extractionId, ImportRequest req) {
        ResumeExtraction extraction = get(userId, extractionId);
        int exp = 0, prj = 0, edu = 0, cert = 0, sk = 0, lang = 0;
        for (var c : nn(req.experiences())) {
            items.createExperience(userId, c, ItemSource.CV_IMPORT);
            exp++;
        }
        for (var c : nn(req.projects())) {
            items.createProject(userId, c, ItemSource.CV_IMPORT);
            prj++;
        }
        for (var c : nn(req.educations())) {
            items.createEducation(userId, c, ItemSource.CV_IMPORT);
            edu++;
        }
        for (var c : nn(req.certifications())) {
            items.createCertification(userId, c, ItemSource.CV_IMPORT);
            cert++;
        }
        if (!nn(req.skillIds()).isEmpty()) {
            skills.requireExisting(req.skillIds());
            // skills listed in a CV without employment evidence: the user sets the real level when verifying
            skills.ensureUserSkills(userId, req.skillIds(), Enums.EvidenceLevel.PROJECT_ONLY, false);
            sk = req.skillIds().size();
        }
        if (!nn(req.languages()).isEmpty()) {
            lang = profileService.importLanguages(userId, req.languages());
        }
        extraction.markImported(clock.instant());
        audit.record(userId, "CV_IMPORTED", "RESUME_EXTRACTION", extractionId,
                Map.of("experiences", exp, "projects", prj, "skills", sk));
        return new ImportResult(exp, prj, edu, cert, sk, lang);
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }
}
