package com.jobhunter.resumes.domain;

import com.jobhunter.resumes.domain.ParsedResume.CertificationItem;
import com.jobhunter.resumes.domain.ParsedResume.Contact;
import com.jobhunter.resumes.domain.ParsedResume.EducationItem;
import com.jobhunter.resumes.domain.ParsedResume.ExperienceItem;
import com.jobhunter.resumes.domain.ParsedResume.LanguageItem;
import com.jobhunter.resumes.domain.ParsedResume.ProjectItem;
import com.jobhunter.resumes.domain.ParsedResume.SkillItem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline CV parser (no AI, no external service). It is deliberately conservative: anything it is not
 * sure about stays in the section text for the user to review. Supports headings in ES/EN/IT/DE/FR/PT.
 */
public class HeuristicResumeParser {

    public enum Section { SUMMARY, EXPERIENCE, EDUCATION, PROJECTS, SKILLS, LANGUAGES, CERTIFICATIONS, OTHER }

    private static final Map<Section, List<String>> HEADINGS = Map.of(
            Section.SUMMARY, List.of("resumen", "perfil", "perfil profesional", "sobre mi", "acerca de mi", "summary",
                    "profile", "professional summary", "about me", "about", "objective", "objetivo", "profilo",
                    "riepilogo", "profil", "zusammenfassung", "resume", "sommario", "perfil pessoal"),
            Section.EXPERIENCE, List.of("experiencia", "experiencia laboral", "experiencia profesional",
                    "historial laboral", "experience", "work experience", "professional experience",
                    "employment history", "employment", "work history", "esperienza", "esperienza lavorativa",
                    "esperienze professionali", "berufserfahrung", "erfahrung", "experience professionnelle",
                    "experiences professionnelles", "experiência profissional"),
            Section.EDUCATION, List.of("educacion", "formacion", "formacion academica", "estudios", "education",
                    "academic background", "istruzione", "formazione", "ausbildung", "bildung", "formation",
                    "educação", "formação"),
            Section.PROJECTS, List.of("proyectos", "proyectos personales", "projects", "personal projects",
                    "side projects", "progetti", "projekte", "projets", "projetos"),
            Section.SKILLS, List.of("habilidades", "habilidades tecnicas", "conocimientos", "conocimientos tecnicos",
                    "competencias", "tecnologias", "skills", "technical skills", "tech stack", "technologies",
                    "competenze", "competenze tecniche", "kenntnisse", "fahigkeiten", "competences",
                    "competences techniques", "habilidades técnicas", "stack tecnologico"),
            Section.LANGUAGES, List.of("idiomas", "languages", "lingue", "sprachen", "langues", "lenguas"),
            Section.CERTIFICATIONS, List.of("certificaciones", "certificados", "cursos", "certifications",
                    "certificates", "courses", "licenses & certifications", "certificazioni", "zertifikate",
                    "zertifizierungen", "certificats", "certificações"));

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w/])\\+?\\d[\\d\\s().-]{7,18}\\d(?![\\w/])");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/[\\w\\-./%?=&#]*)?");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[•·▪●◦\\-*–]|\\d+[.)])\\s+");

    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("espanol", "es"), Map.entry("castellano", "es"), Map.entry("spanish", "es"), Map.entry("spagnolo", "es"), Map.entry("spanisch", "es"), Map.entry("espagnol", "es"),
            Map.entry("ingles", "en"), Map.entry("english", "en"), Map.entry("inglese", "en"), Map.entry("englisch", "en"), Map.entry("anglais", "en"),
            Map.entry("italiano", "it"), Map.entry("italian", "it"), Map.entry("italienisch", "it"), Map.entry("italien", "it"),
            Map.entry("aleman", "de"), Map.entry("german", "de"), Map.entry("tedesco", "de"), Map.entry("deutsch", "de"), Map.entry("allemand", "de"),
            Map.entry("frances", "fr"), Map.entry("french", "fr"), Map.entry("francese", "fr"), Map.entry("franzosisch", "fr"), Map.entry("francais", "fr"),
            Map.entry("portugues", "pt"), Map.entry("portuguese", "pt"), Map.entry("portoghese", "pt"),
            Map.entry("quechua", "qu"), Map.entry("aymara", "ay"), Map.entry("guarani", "gn"),
            Map.entry("chino", "zh"), Map.entry("chinese", "zh"), Map.entry("japones", "ja"), Map.entry("japanese", "ja"));

    private final Function<String, List<SkillItem>> skillFinder;

    /** @param skillFinder finds catalogue skills in a text (see SkillDictionary) */
    public HeuristicResumeParser(Function<String, List<SkillItem>> skillFinder) {
        this.skillFinder = skillFinder;
    }

    public ParsedResume parse(String text) {
        List<String> warnings = new ArrayList<>();
        Map<Section, StringBuilder> sections = split(text);
        String header = sections.containsKey(Section.OTHER) ? sections.get(Section.OTHER).toString() : "";

        Contact contact = contact(text, header);
        String summary = sections.containsKey(Section.SUMMARY) ? clean(sections.get(Section.SUMMARY).toString()) : null;

        List<ExperienceItem> experiences = sections.containsKey(Section.EXPERIENCE)
                ? experiences(sections.get(Section.EXPERIENCE).toString()) : List.of();
        if (!sections.containsKey(Section.EXPERIENCE)) warnings.add("No se encontró una sección de experiencia laboral");

        List<EducationItem> educations = sections.containsKey(Section.EDUCATION)
                ? educations(sections.get(Section.EDUCATION).toString()) : List.of();
        List<ProjectItem> projects = sections.containsKey(Section.PROJECTS)
                ? projects(sections.get(Section.PROJECTS).toString()) : List.of();
        List<CertificationItem> certifications = sections.containsKey(Section.CERTIFICATIONS)
                ? certifications(sections.get(Section.CERTIFICATIONS).toString()) : List.of();

        String skillSource = sections.containsKey(Section.SKILLS) ? sections.get(Section.SKILLS).toString() : text;
        List<SkillItem> skills = skillFinder.apply(skillSource);
        if (!sections.containsKey(Section.SKILLS)) {
            warnings.add("No se encontró una sección de habilidades; las tecnologías se detectaron en todo el documento");
        }

        List<LanguageItem> languages = languages(sections.containsKey(Section.LANGUAGES)
                ? sections.get(Section.LANGUAGES).toString() : "");

        Map<String, String> raw = new LinkedHashMap<>();
        sections.forEach((k, v) -> raw.put(k.name(), clean(v.toString())));
        warnings.add("Todo lo detectado queda SIN VERIFICAR hasta que lo revises e importes");
        return new ParsedResume(contact, summary, experiences, educations, projects, certifications, skills, languages,
                raw, warnings);
    }

    // ---- sections ----

    Map<Section, StringBuilder> split(String text) {
        Map<Section, StringBuilder> result = new LinkedHashMap<>();
        Section current = Section.OTHER;
        for (String line : text.split("\n")) {
            Section heading = heading(line);
            if (heading != null) {
                current = heading;
                result.computeIfAbsent(current, k -> new StringBuilder());
                continue;
            }
            result.computeIfAbsent(current, k -> new StringBuilder()).append(line).append('\n');
        }
        return result;
    }

    static Section heading(String line) {
        String t = fold(line).replaceAll("[:|_=#*]+", " ").trim();
        if (t.isEmpty() || t.length() > 40) return null;
        for (var entry : HEADINGS.entrySet()) {
            for (String h : entry.getValue()) {
                if (t.equals(fold(h))) return entry.getKey();
            }
        }
        return null;
    }

    // ---- contact ----

    private static Contact contact(String text, String header) {
        String email = first(EMAIL, text);
        String phone = null;
        Matcher pm = PHONE.matcher(header.isBlank() ? text : header);
        while (pm.find()) {
            String candidate = pm.group().trim();
            long digits = candidate.chars().filter(Character::isDigit).count();
            if (digits >= 8 && digits <= 15 && DateRanges.find(candidate).isEmpty()) {
                phone = candidate;
                break;
            }
        }
        String github = null, linkedin = null, website = null;
        Matcher um = URL.matcher(text);
        while (um.find()) {
            String url = um.group();
            if (url.contains("@") || !url.contains(".")) continue;
            String lower = url.toLowerCase(Locale.ROOT);
            String normalized = lower.startsWith("http") ? url : "https://" + url;
            if (lower.contains("github.com/") && github == null) github = normalized;
            else if (lower.contains("linkedin.com/") && linkedin == null) linkedin = normalized;
            else if (website == null && (lower.startsWith("http") || lower.startsWith("www.")) && !lower.contains("github.com")
                    && !lower.contains("linkedin.com")) website = normalized;
        }
        String fullName = null;
        for (String line : header.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || EMAIL.matcher(l).find() || PHONE.matcher(l).find() || l.length() > 60) continue;
            if (l.split("\\s+").length >= 2 && l.split("\\s+").length <= 5 && l.matches("[\\p{L} .'-]+")) {
                fullName = l;
                break;
            }
        }
        return new Contact(fullName, email, phone, github, linkedin, website, null);
    }

    // ---- experience ----

    private List<ExperienceItem> experiences(String section) {
        List<ExperienceItem> items = new ArrayList<>();
        for (Block block : blocks(section)) {
            List<String> heads = block.headLines();
            String title = heads.isEmpty() ? null : heads.get(0);
            String company = heads.size() > 1 ? heads.get(1) : null;
            if (title != null && company == null) {
                String[] split = splitTitleCompany(title);
                title = split[0];
                company = split[1];
            }
            List<String> bullets = new ArrayList<>();
            StringBuilder description = new StringBuilder();
            for (String l : block.bodyLines()) {
                if (BULLET.matcher(l).find()) bullets.add(BULLET.matcher(l).replaceFirst("").trim());
                else description.append(l).append(' ');
            }
            items.add(new ExperienceItem(trimOrNull(title), trimOrNull(company), block.range().start(),
                    block.range().end(), block.range().current(), trimOrNull(description.toString()), bullets,
                    skillFinder.apply(block.text()).stream().map(SkillItem::skillId).toList(), block.text()));
        }
        return items;
    }

    /** "Backend Developer at Acme", "Backend Developer | Acme", "Acme — Backend Developer" */
    static String[] splitTitleCompany(String line) {
        for (String sep : new String[]{" at ", " en ", " @ ", " | ", " - ", " – ", " — ", ", "}) {
            int i = line.indexOf(sep);
            if (i > 0) return new String[]{line.substring(0, i).trim(), line.substring(i + sep.length()).trim()};
        }
        return new String[]{line, null};
    }

    private List<EducationItem> educations(String section) {
        List<EducationItem> items = new ArrayList<>();
        for (Block block : blocks(section)) {
            List<String> heads = block.headLines();
            String first = heads.isEmpty() ? null : heads.get(0);
            String second = heads.size() > 1 ? heads.get(1) : null;
            // Institutions usually contain these words; otherwise assume "degree / institution" order
            boolean firstIsInstitution = first != null && fold(first).matches(".*(universidad|university|universita|universitat|universite|instituto|institute|college|escuela|school|politecnico|tecnologico).*");
            String institution = firstIsInstitution ? first : (second != null ? second : first);
            String degree = firstIsInstitution ? second : (second != null ? first : null);
            items.add(new EducationItem(trimOrNull(institution), trimOrNull(degree), block.range().start(),
                    block.range().end(), block.text()));
        }
        if (items.isEmpty() && !section.isBlank()) {
            for (String line : section.split("\n")) {
                if (!line.isBlank()) items.add(new EducationItem(line.trim(), null, null, null, line.trim()));
            }
        }
        return items;
    }

    private List<ProjectItem> projects(String section) {
        List<ProjectItem> items = new ArrayList<>();
        StringBuilder current = null;
        String name = null;
        for (String line : section.split("\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            boolean bullet = BULLET.matcher(l).find();
            if (!bullet && l.length() <= 80 && (current == null || current.length() > 0)) {
                if (name != null) items.add(project(name, current.toString()));
                name = l.replaceAll("[:|]+$", "").trim();
                current = new StringBuilder();
            } else if (current != null) {
                current.append(BULLET.matcher(l).replaceFirst("")).append('\n');
            }
        }
        if (name != null) items.add(project(name, current.toString()));
        return items;
    }

    private ProjectItem project(String name, String description) {
        String text = name + "\n" + description;
        return new ProjectItem(name, trimOrNull(description), skillFinder.apply(text).stream().map(SkillItem::skillId).toList(), text.trim());
    }

    private static List<CertificationItem> certifications(String section) {
        List<CertificationItem> items = new ArrayList<>();
        for (String line : section.split("\n")) {
            String l = BULLET.matcher(line).replaceFirst("").trim();
            if (l.isEmpty()) continue;
            var year = Pattern.compile("(19|20)\\d{2}").matcher(l);
            var date = year.find() ? DateRanges.point(year.group()) : null;
            String[] parts = splitTitleCompany(l.replaceAll("\\(?(19|20)\\d{2}\\)?", "").trim());
            items.add(new CertificationItem(parts[0], parts[1], date, l));
        }
        return items;
    }

    static List<LanguageItem> languages(String section) {
        List<LanguageItem> items = new ArrayList<>();
        for (String line : section.split("[\n,;]")) {
            String folded = fold(line);
            for (var e : LANGUAGE_NAMES.entrySet()) {
                if (Pattern.compile("\\b" + e.getKey() + "\\b").matcher(folded).find()
                        && items.stream().noneMatch(i -> i.languageCode().equals(e.getValue()))) {
                    items.add(new LanguageItem(e.getValue(), level(folded), line.trim()));
                }
            }
        }
        return items;
    }

    /** CEFR level from explicit codes or common words; null when unclear (user decides). */
    static String level(String folded) {
        Matcher cefr = Pattern.compile("\\b([abc][12])\\b").matcher(folded);
        if (cefr.find()) return cefr.group(1).toUpperCase(Locale.ROOT);
        if (folded.matches(".*\\b(nativ[oae]?|native|madrelingua|muttersprache|langue maternelle|lengua materna|materno)\\b.*")) return "NATIVE";
        if (folded.matches(".*\\b(bilingue|bilingual|fluent|fluido|fluente|fließend|fliessend|courant)\\b.*")) return "C1";
        if (folded.matches(".*\\b(avanzado|advanced|avanzato|fortgeschritten|avance)\\b.*")) return "C1";
        if (folded.matches(".*\\b(intermedio avanzado|upper intermediate)\\b.*")) return "B2";
        if (folded.matches(".*\\b(intermedio|intermediate|intermediaire|mittelstufe)\\b.*")) return "B1";
        if (folded.matches(".*\\b(basico|basic|elementary|base|grundkenntnisse|debutant|principiante)\\b.*")) return "A2";
        return null;
    }

    // ---- blocks: a date range line plus the lines around it ----

    record Block(List<String> headLines, DateRanges.Range range, List<String> bodyLines, String text) {
    }

    static List<Block> blocks(String section) {
        List<String> lines = new ArrayList<>();
        for (String l : section.split("\n")) {
            if (!l.isBlank()) lines.add(l.trim());
        }
        List<Integer> dateLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (DateRanges.find(lines.get(i)).isPresent()) dateLines.add(i);
        }
        List<Block> blocks = new ArrayList<>();
        for (int b = 0; b < dateLines.size(); b++) {
            int dateIdx = dateLines.get(b);
            int prevEnd = b == 0 ? 0 : dateLines.get(b - 1) + 1;
            int nextDate = b + 1 < dateLines.size() ? dateLines.get(b + 1) : lines.size();
            DateRanges.Range range = DateRanges.find(lines.get(dateIdx)).orElseThrow();
            List<String> heads = new ArrayList<>();
            String rest = (lines.get(dateIdx).substring(0, range.matchStart()) + " "
                    + lines.get(dateIdx).substring(range.matchEnd())).replaceAll("[|()·,–—-]+\\s*$", "").replaceAll("^\\s*[|()·,–—-]+", "").trim();
            // head lines: up to 2 non-bullet lines right before the date line (that are not the previous block body)
            int headStart = Math.max(prevEnd, dateIdx - 2);
            for (int i = headStart; i < dateIdx; i++) {
                if (!BULLET.matcher(lines.get(i)).find()) heads.add(lines.get(i));
            }
            if (!rest.isBlank()) heads.add(rest);
            // body: lines after the date until the head lines of the next block
            int bodyEnd = nextDate;
            if (b + 1 < dateLines.size()) {
                int nextHeadStart = Math.max(dateIdx + 1, nextDate - 2);
                while (nextHeadStart < nextDate && BULLET.matcher(lines.get(nextHeadStart)).find()) nextHeadStart++;
                bodyEnd = nextHeadStart;
            }
            List<String> body = new ArrayList<>(lines.subList(dateIdx + 1, Math.max(dateIdx + 1, bodyEnd)));
            if (heads.isEmpty() && !body.isEmpty() && !BULLET.matcher(body.get(0)).find()) {
                heads.add(body.remove(0));
            }
            List<String> all = new ArrayList<>(heads);
            all.add(lines.get(dateIdx));
            all.addAll(body);
            blocks.add(new Block(heads, range, body, String.join("\n", all)));
        }
        return blocks;
    }

    // ---- helpers ----

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    private static String clean(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : (t.length() > 200 ? t.substring(0, 200) : t);
    }
}
