package com.jobhunter.resumes.application;

import com.jobhunter.profiles.application.ProfileSnapshotService.Contact;
import com.jobhunter.profiles.application.ProfileSnapshotService.Snapshot;
import com.jobhunter.resumes.domain.ResumeDocument;
import com.jobhunter.resumes.domain.ResumeDocument.Basics;
import com.jobhunter.resumes.domain.ResumeDocument.Bullet;
import com.jobhunter.resumes.domain.ResumeDocument.BulletStatus;
import com.jobhunter.resumes.domain.ResumeDocument.Link;
import com.jobhunter.resumes.domain.ResumeLabels;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a resume document from verified profile facts only. Text is copied as written by the user (no AI);
 * every bullet references the fact it comes from.
 */
@Component
public class ResumeDocumentBuilder {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String> CATEGORY_ORDER = List.of("PROGRAMMING_LANGUAGE", "FRAMEWORK", "DATABASE",
            "CLOUD", "TOOL", "METHODOLOGY", "OTHER");

    public ResumeDocument build(String fullName, Snapshot s, Contact c, String language) {
        List<Link> links = new ArrayList<>();
        if (c.linkedinUrl() != null) links.add(new Link("LinkedIn", c.linkedinUrl()));
        if (c.githubUrl() != null) links.add(new Link("GitHub", c.githubUrl()));
        if (c.portfolioUrl() != null) links.add(new Link("Portfolio", c.portfolioUrl()));
        if (c.websiteUrl() != null) links.add(new Link("Web", c.websiteUrl()));
        String location = c.city() == null ? c.country() : (c.country() == null ? c.city() : c.city() + ", " + c.country());
        Basics basics = new Basics(fullName, s.headline(), c.email(), c.phone(), location, links);

        List<ResumeDocument.ExperienceEntry> experience = s.experiences().stream().map(e -> {
            List<Bullet> bullets = new ArrayList<>();
            if (e.description() != null) bullets.add(new Bullet(e.description(), List.of(e.ref()), BulletStatus.GROUNDED));
            e.achievements().forEach(a -> bullets.add(new Bullet(a.text(), List.of(a.ref()), BulletStatus.GROUNDED)));
            String loc = e.location() != null ? e.location() : e.country();
            return new ResumeDocument.ExperienceEntry(e.ref(), e.company(), e.title(), loc, ym(e.startDate()),
                    ym(e.endDate()), e.current(), bullets);
        }).toList();

        List<ResumeDocument.ProjectEntry> projects = s.projects().stream().map(p -> {
            List<Bullet> bullets = new ArrayList<>();
            if (p.description() != null) bullets.add(new Bullet(p.description(), List.of(p.ref()), BulletStatus.GROUNDED));
            p.highlights().forEach(h -> bullets.add(new Bullet(h.text(), List.of(h.ref()), BulletStatus.GROUNDED)));
            return new ResumeDocument.ProjectEntry(p.ref(), p.kind(), p.name(), p.role(), p.url(), bullets);
        }).toList();

        List<ResumeDocument.EducationEntry> education = s.educations().stream()
                .map(e -> new ResumeDocument.EducationEntry(e.ref(), e.institution(), e.degree(), e.field(),
                        ym(e.startDate()), ym(e.endDate()), e.status())).toList();

        List<ResumeDocument.CertificationEntry> certifications = s.certifications().stream()
                .map(x -> new ResumeDocument.CertificationEntry(x.ref(), x.name(), x.issuer(), ym(x.issuedAt()), x.url()))
                .toList();

        Map<String, List<ResumeDocument.SkillEntry>> grouped = new LinkedHashMap<>();
        CATEGORY_ORDER.forEach(cat -> grouped.put(cat, new ArrayList<>()));
        s.skills().stream()
                // learning-only skills are not claimed as competencies on a resume
                .filter(sk -> !"LEARNING".equals(sk.evidence()))
                .forEach(sk -> grouped.computeIfAbsent(sk.category() == null ? "OTHER" : sk.category(), k -> new ArrayList<>())
                        .add(new ResumeDocument.SkillEntry(sk.ref(), sk.name(), sk.evidence())));
        List<ResumeDocument.SkillGroup> skills = grouped.entrySet().stream().filter(en -> !en.getValue().isEmpty())
                .map(en -> new ResumeDocument.SkillGroup(ResumeLabels.get(language, en.getKey()), en.getValue()))
                .toList();

        List<ResumeDocument.LanguageEntry> languages = s.languages().stream()
                .map(l -> new ResumeDocument.LanguageEntry(l.ref(), l.code(), l.level())).toList();

        return new ResumeDocument(basics, s.summary(), experience, projects, education, certifications, skills, languages);
    }

    private static String ym(LocalDate d) {
        return d == null ? null : d.format(YM);
    }
}
