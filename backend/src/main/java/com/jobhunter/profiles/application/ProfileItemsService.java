package com.jobhunter.profiles.application;

import com.jobhunter.profiles.application.Commands.CertificationCommand;
import com.jobhunter.profiles.application.Commands.EducationCommand;
import com.jobhunter.profiles.application.Commands.ExperienceCommand;
import com.jobhunter.profiles.application.Commands.ProjectCommand;
import com.jobhunter.profiles.domain.Certification;
import com.jobhunter.profiles.domain.Education;
import com.jobhunter.profiles.domain.Enums.EvidenceLevel;
import com.jobhunter.profiles.domain.Enums.ItemSource;
import com.jobhunter.profiles.domain.Enums.ProjectKind;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.Project;
import com.jobhunter.profiles.infrastructure.CertificationRepository;
import com.jobhunter.profiles.infrastructure.EducationRepository;
import com.jobhunter.profiles.infrastructure.ExperienceRepository;
import com.jobhunter.profiles.infrastructure.ProjectRepository;
import com.jobhunter.shared.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** CRUD of experiences, projects, education and certifications. Every access is scoped by user id. */
@Service
public class ProfileItemsService {

    private final ExperienceRepository experiences;
    private final ProjectRepository projects;
    private final EducationRepository educations;
    private final CertificationRepository certifications;
    private final SkillService skillService;
    private final Clock clock;

    public ProfileItemsService(ExperienceRepository experiences, ProjectRepository projects,
                               EducationRepository educations, CertificationRepository certifications,
                               SkillService skillService, Clock clock) {
        this.experiences = experiences;
        this.projects = projects;
        this.educations = educations;
        this.certifications = certifications;
        this.skillService = skillService;
        this.clock = clock;
    }

    // ---- experiences ----

    @Transactional(readOnly = true)
    public List<Experience> listExperiences(long userId) {
        return experiences.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Experience::getStartDate).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public Experience getExperience(long userId, long id) {
        return experiences.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Experiencia"));
    }

    @Transactional
    public Experience createExperience(long userId, ExperienceCommand c, ItemSource source) {
        Experience e = new Experience(userId, source);
        apply(e, c);
        Experience saved = experiences.save(e);
        skillService.ensureUserSkills(userId, saved.getSkillIds(), EvidenceLevel.PROFESSIONAL, source == ItemSource.MANUAL);
        return saved;
    }

    @Transactional
    public Experience updateExperience(long userId, long id, ExperienceCommand c) {
        Experience e = getExperience(userId, id);
        e.checkVersion(c.version());
        apply(e, c);
        skillService.ensureUserSkills(userId, e.getSkillIds(), EvidenceLevel.PROFESSIONAL, e.isVerified());
        return e;
    }

    @Transactional
    public void deleteExperience(long userId, long id) {
        getExperience(userId, id).markDeleted(clock.instant());
    }

    private void apply(Experience e, ExperienceCommand c) {
        skillService.requireExisting(c.skillIds());
        e.update(c.companyName().trim(), blank(c.companyUrl()), c.positionTitle().trim(), c.employmentType(),
                c.workModality(), blank(c.location()), blank(c.country()), c.startDate(), c.endDate(), c.current(),
                blank(c.description()), c.achievements(), c.skillIds() == null ? null : new LinkedHashSet<>(c.skillIds()));
    }

    // ---- projects ----

    @Transactional(readOnly = true)
    public List<Project> listProjects(long userId) {
        return projects.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Project::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Project getProject(long userId, long id) {
        return projects.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Proyecto"));
    }

    @Transactional
    public Project createProject(long userId, ProjectCommand c, ItemSource source) {
        Project p = new Project(userId, source);
        apply(p, c);
        Project saved = projects.save(p);
        skillService.ensureUserSkills(userId, saved.getSkillIds(), evidenceFor(c.kind()), source == ItemSource.MANUAL);
        return saved;
    }

    @Transactional
    public Project updateProject(long userId, long id, ProjectCommand c) {
        Project p = getProject(userId, id);
        p.checkVersion(c.version());
        apply(p, c);
        skillService.ensureUserSkills(userId, p.getSkillIds(), evidenceFor(c.kind()), p.isVerified());
        return p;
    }

    @Transactional
    public void deleteProject(long userId, long id) {
        getProject(userId, id).markDeleted(clock.instant());
    }

    /** Project work is never professional evidence; academic projects map to ACADEMIC. */
    static EvidenceLevel evidenceFor(ProjectKind kind) {
        return kind == ProjectKind.ACADEMIC ? EvidenceLevel.ACADEMIC : EvidenceLevel.PROJECT_ONLY;
    }

    private void apply(Project p, ProjectCommand c) {
        skillService.requireExisting(c.skillIds());
        p.update(c.kind(), c.name().trim(), blank(c.role()), blank(c.description()), blank(c.url()),
                blank(c.repoUrl()), c.startDate(), c.endDate(), c.highlights(),
                c.skillIds() == null ? null : new LinkedHashSet<>(c.skillIds()));
    }

    // ---- education ----

    @Transactional(readOnly = true)
    public List<Education> listEducations(long userId) {
        return educations.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Education::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Education getEducation(long userId, long id) {
        return educations.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Educación"));
    }

    @Transactional
    public Education createEducation(long userId, EducationCommand c, ItemSource source) {
        Education e = new Education(userId, source);
        e.update(c.institution().trim(), blank(c.degree()), blank(c.fieldOfStudy()), c.startDate(), c.endDate(),
                c.status(), blank(c.description()));
        return educations.save(e);
    }

    @Transactional
    public Education updateEducation(long userId, long id, EducationCommand c) {
        Education e = getEducation(userId, id);
        e.checkVersion(c.version());
        e.update(c.institution().trim(), blank(c.degree()), blank(c.fieldOfStudy()), c.startDate(), c.endDate(),
                c.status(), blank(c.description()));
        return e;
    }

    @Transactional
    public void deleteEducation(long userId, long id) {
        getEducation(userId, id).markDeleted(clock.instant());
    }

    // ---- certifications ----

    @Transactional(readOnly = true)
    public List<Certification> listCertifications(long userId) {
        return certifications.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Certification::getIssuedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Certification getCertification(long userId, long id) {
        return certifications.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Certificación"));
    }

    @Transactional
    public Certification createCertification(long userId, CertificationCommand c, ItemSource source) {
        Certification cert = new Certification(userId, source);
        cert.update(c.name().trim(), blank(c.issuer()), c.issuedAt(), c.expiresAt(), blank(c.credentialId()),
                blank(c.credentialUrl()));
        return certifications.save(cert);
    }

    @Transactional
    public Certification updateCertification(long userId, long id, CertificationCommand c) {
        Certification cert = getCertification(userId, id);
        cert.checkVersion(c.version());
        cert.update(c.name().trim(), blank(c.issuer()), c.issuedAt(), c.expiresAt(), blank(c.credentialId()),
                blank(c.credentialUrl()));
        return cert;
    }

    @Transactional
    public void deleteCertification(long userId, long id) {
        getCertification(userId, id).markDeleted(clock.instant());
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
