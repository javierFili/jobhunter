package com.jobhunter.jobs.domain;

import com.jobhunter.jobs.domain.JobClassification.RequiredLanguage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based classification of modality, remote scope, Bolivia eligibility, engagement and contract type.
 * Principle: a job is ELIGIBLE/LIKELY for Bolivia only with explicit evidence; otherwise UNKNOWN (never guessed).
 * Bolivia is UTC-4 all year.
 */
public final class EligibilityClassifier {

    private static final int BOLIVIA_UTC_OFFSET = -4;
    /** Sources that only publish remote jobs. */
    private static final Set<String> REMOTE_ONLY_SOURCES = Set.of("remotive", "himalayas", "jobicy", "remoteok");

    private static final Pattern WORLDWIDE = Pattern.compile(
            "\\b(anywhere(?: in the world)?|worldwide|world-wide|work from anywhere|globally remote|fully remote \\(global\\)|global remote|remote[ -]global|100% remote worldwide|cualquier (?:lugar|pa[ií]s)(?: del mundo)?|todo el mundo|en cualquier parte del mundo)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LATAM = Pattern.compile(
            "\\b(latam|latin america|latinoam[eé]rica|am[eé]rica latina|south america|sudam[eé]rica|suram[eé]rica)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMERICAS = Pattern.compile("\\b(the americas|americas|am[eé]ricas)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EUROPE_ONLY = Pattern.compile(
            "\\b(eu only|europe only|based in (?:the )?(?:eu|europe)|within (?:the )?(?:eu|europe|european union)|eu work permit|right to work in (?:the )?(?:eu|europe|uk)|eu citizens?|remote - european union|(?:emea|europe)[- ]based)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY_ONLY = Pattern.compile(
            "\\b((?:us|usa|u\\.s\\.|uk|canada|united states|united kingdom|germany|spain|india)[ -](?:only|based)|(?:must|need to|required to) (?:be )?(?:based|located|reside|live)(?: in)? (?:the )?(?:us|usa|u\\.s\\.|united states|uk|united kingdom|canada|germany|spain|eu|europe)|(?:authorized|authorised|eligible|legally able) to work in (?:the )?(?:us|usa|u\\.s\\.|united states|uk|united kingdom|canada)|us citizens?(?:hip)?|security clearance|green card|residir en (?:espa[ñn]a|m[eé]xico|argentina|chile|colombia)|residencia en (?:espa[ñn]a|m[eé]xico|argentina|chile|colombia))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMEZONE_RANGE = Pattern.compile(
            "(?:utc|gmt)\\s*([+-−]\\s*\\d{1,2})(?::\\d{2})?\\s*(?:to|-|–|and|a|hasta)\\s*(?:utc|gmt)?\\s*([+-−]\\s*\\d{1,2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPONSORSHIP = Pattern.compile(
            "\\b(visa sponsorship|sponsor(?:ship)? (?:your |a )?(?:work )?visa|relocation (?:support|package|assistance)|we (?:will )?sponsor|patrocinio de visa|apoyo (?:a la|para) reubicaci[oó]n|relocation provided)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_SPONSORSHIP = Pattern.compile(
            "\\b(no (?:visa )?sponsorship|(?:unable|not able|cannot|can't|do not|don't) (?:to )?(?:offer |provide )?(?:visa )?sponsor)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRACTOR = Pattern.compile(
            "\\b(independent contractor|contractor|1099|b2b contract|contrato de prestaci[oó]n de servicios|contrato por servicios|freelance contract)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FREELANCE = Pattern.compile("\\b(freelance|freelancer|project[- ]based|por proyecto)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EOR = Pattern.compile("\\b(employer of record|eor|deel|remote\\.com|oyster|velocity global|via an eor)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONSITE = Pattern.compile("\\b(on-?site|in[- ]office|presencial|in person|office-based)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HYBRID = Pattern.compile("\\b(hybrid|h[ií]brido|ibrido|hybride)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOTE = Pattern.compile("\\b(remote|remoto|teletrabajo|home office|remota|da remoto|t[ée]l[ée]travail)\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> LANGUAGE_WORDS = Map.ofEntries(
            Map.entry("english", "en"), Map.entry("inglés", "en"), Map.entry("ingles", "en"), Map.entry("inglese", "en"),
            Map.entry("englisch", "en"), Map.entry("german", "de"), Map.entry("deutsch", "de"), Map.entry("alemán", "de"),
            Map.entry("aleman", "de"), Map.entry("tedesco", "de"), Map.entry("spanish", "es"), Map.entry("español", "es"),
            Map.entry("espanol", "es"), Map.entry("castellano", "es"), Map.entry("french", "fr"), Map.entry("francés", "fr"),
            Map.entry("frances", "fr"), Map.entry("français", "fr"), Map.entry("italian", "it"), Map.entry("italiano", "it"),
            Map.entry("portuguese", "pt"), Map.entry("portugués", "pt"), Map.entry("dutch", "nl"));
    private static final Pattern LANGUAGE_REQ = Pattern.compile(
            "\\b(fluent|fluency|native|proficient|proficiency|business|professional|advanced|excellent|strong|written and spoken|avanzado|nativo|fluido|c1|c2|b2|b1)\\b[^.\\n]{0,40}?\\b(english|ingl[eé]s|inglese|englisch|german|deutsch|alem[aá]n|tedesco|spanish|espa[nñ]ol|castellano|french|franc[eé]s|fran[cç]ais|italian|italiano|portuguese|portugu[eé]s|dutch)\\b"
                    + "|\\b(english|ingl[eé]s|inglese|englisch|german|deutsch|alem[aá]n|tedesco|spanish|espa[nñ]ol|french|franc[eé]s|fran[cç]ais|italian|italiano|portuguese|portugu[eé]s|dutch)\\b[^.\\n]{0,25}?\\b(fluent|native|c1|c2|b2|b1|avanzado|required|requerido|obligatorio|mandatory|is a must)\\b",
            Pattern.CASE_INSENSITIVE);

    private EligibilityClassifier() {
    }

    public static JobClassification classify(JobOfferDraft d) {
        String location = d.locationText() == null ? "" : d.locationText();
        String text = d.descriptionText() == null ? "" : d.descriptionText();
        String head = text.length() > 4000 ? text.substring(0, 4000) : text;
        String all = d.title() + "\n" + location + "\n" + head;

        // --- country of the job (where the employer/office is) ---
        String country = d.countryHint() != null ? d.countryHint() : Geo.findIn(location).orElse(null);

        // --- modality ---
        String modality = modality(d, location, all);

        // --- structured allowed-location list ---
        List<String> allowedIso = new ArrayList<>();
        boolean structuredWorldwide = false;
        boolean structuredLatam = false;
        boolean structuredEurope = false;
        for (String loc : d.allowedLocations()) {
            Optional<String> iso = Geo.iso(loc);
            if (iso.isPresent()) allowedIso.add(iso.get());
            else if (WORLDWIDE.matcher(loc).find()) structuredWorldwide = true;
            else if (LATAM.matcher(loc).find() || AMERICAS.matcher(loc).find()) structuredLatam = true;
            else if (loc.toLowerCase(Locale.ROOT).matches(".*\\b(europe|eu|european union|emea)\\b.*")) structuredEurope = true;
        }

        String scope = "UNKNOWN";
        String eligibility = "UNKNOWN";
        String evidence = null;

        if ("REMOTE".equals(modality)) {
            Matcher m;
            if (!allowedIso.isEmpty() || structuredLatam || structuredEurope || structuredWorldwide) {
                if (allowedIso.contains(Geo.BOLIVIA)) {
                    scope = allowedIso.stream().allMatch(Geo.LATAM::contains) ? "LATAM" : "COUNTRY_RESTRICTED";
                    eligibility = "ELIGIBLE";
                    evidence = "La fuente lista Bolivia entre los países permitidos (" + allowedIso.size() + " países).";
                } else if (structuredWorldwide) {
                    scope = "WORLDWIDE";
                    eligibility = "ELIGIBLE";
                    evidence = "La fuente indica ubicación: " + String.join(", ", d.allowedLocations());
                } else if (structuredLatam) {
                    scope = "LATAM";
                    eligibility = "LIKELY";
                    evidence = "Restricción de ubicación de la fuente: " + String.join(", ", d.allowedLocations());
                } else {
                    scope = structuredEurope || (!allowedIso.isEmpty() && allowedIso.stream().allMatch(Geo.EUROPE::contains))
                            ? "EUROPE" : "COUNTRY_RESTRICTED";
                    eligibility = "NOT_ELIGIBLE";
                    evidence = "Restringida por la fuente a: " + abbreviate(String.join(", ", d.allowedLocations()), 300);
                }
            } else if ((m = COUNTRY_ONLY.matcher(all)).find()) {
                scope = "COUNTRY_RESTRICTED";
                eligibility = "NOT_ELIGIBLE";
                evidence = quote(all, m);
            } else if ((m = EUROPE_ONLY.matcher(all)).find()) {
                scope = "EUROPE";
                eligibility = "NOT_ELIGIBLE";
                evidence = quote(all, m);
            } else if ((m = LATAM.matcher(location)).find()) {
                scope = "LATAM";
                eligibility = "LIKELY";
                evidence = quote(location, m);
            } else if ((m = LATAM.matcher(head)).find()) {
                scope = "LATAM";
                eligibility = "LIKELY";
                evidence = quote(head, m);
            } else if ((m = WORLDWIDE.matcher(location)).find()) {
                scope = "WORLDWIDE";
                eligibility = "ELIGIBLE";
                evidence = quote(location, m);
            } else if ((m = WORLDWIDE.matcher(head)).find()) {
                scope = "WORLDWIDE";
                eligibility = "LIKELY";
                evidence = quote(head, m);
            } else if ((m = AMERICAS.matcher(location)).find()) {
                scope = "AMERICAS";
                eligibility = "LIKELY";
                evidence = quote(location, m);
            }

            // time zone restrictions can downgrade/upgrade the decision
            TimezoneVerdict tz = timezone(d.timezoneOffsets(), all);
            if (tz != null) {
                if (!tz.includesBolivia() && !"NOT_ELIGIBLE".equals(eligibility)) {
                    scope = "TIMEZONE_RESTRICTED";
                    eligibility = "NOT_ELIGIBLE";
                    evidence = tz.evidence();
                } else if (tz.includesBolivia() && "UNKNOWN".equals(eligibility)) {
                    scope = "TIMEZONE_RESTRICTED";
                    eligibility = "LIKELY";
                    evidence = tz.evidence();
                }
            }
            if ("UNKNOWN".equals(scope) && country != null) {
                if (Geo.BOLIVIA.equals(country)) {
                    eligibility = "ELIGIBLE";
                    evidence = "Empresa ubicada en Bolivia";
                }
            }
        } else if (country != null) {
            if (Geo.BOLIVIA.equals(country)) {
                scope = "UNKNOWN";
                eligibility = "ELIGIBLE";
                evidence = "Puesto " + ("HYBRID".equals(modality) ? "híbrido" : "presencial") + " en Bolivia: " + location;
            } else {
                Matcher sp = SPONSORSHIP.matcher(all);
                if (sp.find() && !NO_SPONSORSHIP.matcher(all).find()) {
                    eligibility = "LIKELY";
                    evidence = "Ofrece patrocinio/reubicación: " + quote(all, sp);
                } else {
                    eligibility = "NOT_ELIGIBLE";
                    evidence = "Puesto " + ("HYBRID".equals(modality) ? "híbrido" : ("ONSITE".equals(modality) ? "presencial" : "no remoto"))
                            + " fuera de Bolivia (" + location + ") sin patrocinio indicado";
                }
            }
        }

        String engagement = engagement(all, modality, eligibility);
        String contract = contract(d.employmentType(), all);
        if ("UNKNOWN".equals(engagement) && "FREELANCE".equals(contract)) engagement = "FREELANCE_PROJECT";
        String region = country != null ? Geo.region(country) : switch (scope) {
            case "LATAM" -> "LATAM";
            case "EUROPE" -> "EUROPE";
            case "WORLDWIDE" -> "WORLDWIDE";
            default -> allowedIso.isEmpty() ? null : Geo.region(allowedIso.get(0));
        };
        List<String> remoteCountries = new ArrayList<>(new LinkedHashSet<>(allowedIso));
        return new JobClassification(modality, scope, remoteCountries, eligibility, abbreviate(evidence, 1000),
                engagement, contract, seniority(d.seniority(), d.title()), country, region, languages(all));
    }

    static String modality(JobOfferDraft d, String location, String all) {
        String wp = d.workplaceType() == null ? "" : d.workplaceType().toLowerCase(Locale.ROOT);
        if (wp.contains("hybrid")) return "HYBRID";
        if (wp.contains("remote")) return "REMOTE";
        if (wp.contains("onsite") || wp.contains("on-site") || wp.contains("office")) return "ONSITE";
        if (REMOTE_ONLY_SOURCES.contains(d.sourceCode())) return "REMOTE";
        if (HYBRID.matcher(location).find()) return "HYBRID";
        if (Boolean.TRUE.equals(d.remote()) || REMOTE.matcher(location).find()) return "REMOTE";
        if (ONSITE.matcher(location).find()) return "ONSITE";
        String title = d.title() == null ? "" : d.title();
        if (REMOTE.matcher(title).find()) return "REMOTE";
        if (HYBRID.matcher(title).find()) return "HYBRID";
        return "UNKNOWN";
    }

    record TimezoneVerdict(boolean includesBolivia, String evidence) {
    }

    static TimezoneVerdict timezone(List<Integer> offsets, String text) {
        if (offsets != null && !offsets.isEmpty()) {
            int min = offsets.stream().min(Integer::compare).orElseThrow();
            int max = offsets.stream().max(Integer::compare).orElseThrow();
            boolean ok = BOLIVIA_UTC_OFFSET >= min - 1 && BOLIVIA_UTC_OFFSET <= max + 1;
            return new TimezoneVerdict(ok, "Zonas horarias permitidas por la fuente: UTC" + fmt(min) + " a UTC" + fmt(max)
                    + (ok ? " (incluye Bolivia, UTC-4)" : " (Bolivia es UTC-4)"));
        }
        Matcher m = TIMEZONE_RANGE.matcher(text);
        if (m.find()) {
            try {
                int a = Integer.parseInt(m.group(1).replace("−", "-").replace(" ", "").replace("+", ""));
                int b = Integer.parseInt(m.group(2).replace("−", "-").replace(" ", "").replace("+", ""));
                int lo = Math.min(a, b);
                int hi = Math.max(a, b);
                boolean ok = BOLIVIA_UTC_OFFSET >= lo - 1 && BOLIVIA_UTC_OFFSET <= hi + 1;
                return new TimezoneVerdict(ok, quote(text, m) + (ok ? " (incluye Bolivia, UTC-4)" : " (Bolivia es UTC-4)"));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static String engagement(String all, String modality, String eligibility) {
        Matcher m;
        if (EOR.matcher(all).find()) return "EMPLOYEE_EOR";
        if (CONTRACTOR.matcher(all).find()) return "CONTRACTOR";
        if (FREELANCE.matcher(all).find()) return "FREELANCE_PROJECT";
        if ((m = SPONSORSHIP.matcher(all)).find() && !NO_SPONSORSHIP.matcher(all).find() && !"REMOTE".equals(modality)) {
            return "SPONSORED_RELOCATION";
        }
        if ("NOT_ELIGIBLE".equals(eligibility)) return "EMPLOYEE_LOCAL";
        return "UNKNOWN";
    }

    static String contract(String raw, String all) {
        String r = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        if (r.matches(".*\\b(intern|internship|pasant[ií]a|praktikum|stage|tirocinio)\\b.*")) return "INTERNSHIP";
        if (r.matches(".*\\b(part ?time|tiempo parcial|teilzeit)\\b.*") || r.contains("parttime")) return "PART_TIME";
        if (r.matches(".*\\b(temp|temporary|temporal|temporaneo)\\b.*")) return "TEMPORARY";
        if (r.matches(".*\\b(freelance)\\b.*")) return "FREELANCE";
        if (r.matches(".*\\b(contract|contractor|contrato)\\b.*")) return "CONTRACT";
        if (r.matches(".*\\b(full ?time|fulltime|tiempo completo|vollzeit|permanent|regular)\\b.*") || r.contains("fulltime")) return "FULL_TIME";
        if (Pattern.compile("\\b(full[- ]time|tiempo completo)\\b", Pattern.CASE_INSENSITIVE).matcher(all).find()) return "FULL_TIME";
        if (Pattern.compile("\\b(part[- ]time|medio tiempo|tiempo parcial)\\b", Pattern.CASE_INSENSITIVE).matcher(all).find()) return "PART_TIME";
        return "UNKNOWN";
    }

    static String seniority(String raw, String title) {
        String t = ((raw == null ? "" : raw) + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        if (t.matches(".*\\b(intern|trainee|junior|jr\\.?|entry[- ]level|graduate|becario)\\b.*")) return "JUNIOR";
        if (t.matches(".*\\b(senior|sr\\.?|lead|staff|principal|head of|architect)\\b.*")) return "SENIOR";
        if (t.matches(".*\\b(mid|mid-level|intermediate|semi ?senior|ssr|mid-senior)\\b.*")) return "MID";
        return null;
    }

    static List<RequiredLanguage> languages(String text) {
        List<RequiredLanguage> result = new ArrayList<>();
        Matcher m = LANGUAGE_REQ.matcher(text);
        while (m.find() && result.size() < 5) {
            String word = (m.group(2) != null ? m.group(2) : m.group(3)).toLowerCase(Locale.ROOT);
            String qualifier = (m.group(1) != null ? m.group(1) : m.group(4)).toLowerCase(Locale.ROOT);
            String code = LANGUAGE_WORDS.get(word);
            if (code == null || result.stream().anyMatch(r -> r.code().equals(code))) continue;
            String level = switch (qualifier) {
                case "native", "nativo" -> "NATIVE";
                case "c2" -> "C2";
                case "c1", "fluent", "fluency", "fluido", "advanced", "avanzado", "proficient", "proficiency", "excellent" -> "C1";
                case "b2", "business", "professional", "strong", "written and spoken" -> "B2";
                case "b1" -> "B1";
                default -> null;
            };
            result.add(new RequiredLanguage(code, level, abbreviate(m.group(), 200)));
        }
        return result;
    }

    private static String quote(String text, Matcher m) {
        int start = Math.max(0, m.start() - 60);
        int end = Math.min(text.length(), m.end() + 60);
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        return "«" + (start > 0 ? "…" : "") + snippet + (end < text.length() ? "…" : "") + "»";
    }

    private static String fmt(int offset) {
        return offset >= 0 ? "+" + offset : String.valueOf(offset);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
