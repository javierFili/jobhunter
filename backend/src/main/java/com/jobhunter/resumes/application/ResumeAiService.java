package com.jobhunter.resumes.application;

import com.jobhunter.ai.application.AiGateway;
import com.jobhunter.ai.domain.AiOutputs;
import com.jobhunter.ai.domain.UntrustedText;
import com.jobhunter.profiles.application.SkillDictionary;
import com.jobhunter.resumes.domain.ParsedResume;
import com.jobhunter.resumes.domain.ResumeExtraction;
import com.jobhunter.shared.json.Json;
import com.jobhunter.users.domain.ConsentType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional AI structuring of an uploaded CV (route CV_PARSE, DeepSeek or Claude). Requires the user's consent
 * AI_PROCESS_CV for the chosen provider; e-mails, phones and links are removed before sending. Contact data keeps the
 * local (heuristic) parse. Result stays UNVERIFIED until the user imports and verifies it.
 */
@Service
public class ResumeAiService {

    private final ResumeUploadService uploads;
    private final AiGateway gateway;
    private final SkillDictionary dictionary;

    public ResumeAiService(ResumeUploadService uploads, AiGateway gateway, SkillDictionary dictionary) {
        this.uploads = uploads;
        this.gateway = gateway;
        this.dictionary = dictionary;
    }

    public ParsedResume parseWithAi(long userId, long extractionId) {
        ResumeExtraction e = uploads.get(userId, extractionId);
        String text = UntrustedText.stripContactData(e.getRawText());
        var result = gateway.run(new AiGateway.Task<>("CV_PARSE", userId, "RESUME_UPLOAD", extractionId,
                Map.of("cv", UntrustedText.wrap("cv_document", text, 40_000)),
                java.util.HexFormat.of().formatHex(com.jobhunter.jobs.domain.JobText.sha256(text)), ConsentType.AI_PROCESS_CV,
                AiOutputs.CvParse.class, false));
        ParsedResume heuristic = Json.read(e.getParsed(), ParsedResume.class);
        ParsedResume merged = toParsed(result.output(), heuristic);
        uploads.replaceParsed(userId, extractionId, merged, "AI");
        return merged;
    }

    ParsedResume toParsed(AiOutputs.CvParse ai, ParsedResume heuristic) {
        List<ParsedResume.ExperienceItem> experiences = ai.experiences().stream().map(x -> new ParsedResume.ExperienceItem(
                x.positionTitle(), x.companyName(), ym(x.startDate()), x.current() ? null : ym(x.endDate()), x.current(),
                x.description(), x.achievements(), skillIds(String.join("\n", x.achievements()) + "\n" + nz(x.description())),
                x.positionTitle() + " — " + x.companyName())).toList();
        List<ParsedResume.EducationItem> educations = ai.educations().stream().map(x -> new ParsedResume.EducationItem(
                x.institution(), x.degree(), ym(x.startDate()), ym(x.endDate()), x.institution())).toList();
        List<ParsedResume.ProjectItem> projects = ai.projects().stream().map(x -> new ParsedResume.ProjectItem(
                x.name(), x.description(), skillIds(String.join(", ", x.technologies()) + "\n" + nz(x.description())), x.name())).toList();
        List<ParsedResume.CertificationItem> certs = ai.certifications().stream().map(x -> new ParsedResume.CertificationItem(
                x.name(), x.issuer(), ym(x.issuedAt()), x.name())).toList();
        Map<Long, ParsedResume.SkillItem> skills = new LinkedHashMap<>();
        dictionary.find(String.join(", ", ai.skills())).forEach(m -> skills.put(m.skillId(), new ParsedResume.SkillItem(m.skillId(), m.name())));
        List<ParsedResume.LanguageItem> languages = ai.languages().stream()
                .map(l -> new ParsedResume.LanguageItem(l.code().toLowerCase(), l.level(), l.code())).toList();
        List<String> warnings = new ArrayList<>();
        warnings.add("Estructurado con IA a partir del texto (sin datos de contacto). Revisa cada elemento antes de importarlo.");
        if (ai.injectionSuspected()) warnings.add("El documento contenía texto que intentaba dar instrucciones a la IA; revisa con cuidado.");
        return new ParsedResume(heuristic == null ? null : heuristic.contact(), ai.summary(), experiences, educations, projects,
                certs, new ArrayList<>(skills.values()), languages, heuristic == null ? Map.of() : heuristic.sections(), warnings);
    }

    private List<Long> skillIds(String text) {
        return dictionary.find(text).stream().map(SkillDictionary.Match::skillId).toList();
    }

    /** "2022-01" / "2022" → first day of month; anything else → null (never guessed). */
    static LocalDate ym(String s) {
        if (s == null) return null;
        try {
            if (s.matches("\\d{4}-\\d{2}")) return LocalDate.parse(s + "-01");
            if (s.matches("\\d{4}")) return LocalDate.parse(s + "-01-01");
        } catch (RuntimeException ignored) {
            // fall through
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
