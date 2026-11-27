package com.jobhunter.matching.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based part of the match score (spec: "deterministic rules for measurable criteria, AI for semantics").
 * Every dimension yields 0–100 plus a human-readable reason in Spanish. Never estimates hiring probability.
 */
public final class DeterministicScorer {

    public static final Map<String, Integer> WEIGHTS = Map.of("skills", 30, "experience", 20, "location", 15,
            "language", 10, "seniority", 10, "compensation", 10, "contract", 5);
    private static final Map<String, Double> EVIDENCE = Map.of("PROFESSIONAL", 1.0, "PROJECT_ONLY", 0.6, "ACADEMIC", 0.4, "LEARNING", 0.2);
    private static final List<String> LEVELS = List.of("A1", "A2", "B1", "B2", "C1", "C2", "NATIVE");

    private DeterministicScorer() {
    }

    public record SkillFact(long skillId, String name, String evidence, BigDecimal years) {
    }

    public record ProfileInput(Map<Long, SkillFact> skills, BigDecimal totalYears, Map<String, String> languages,
                               String preferredSeniority, BigDecimal minSalaryUsdMonth, List<String> contractTypes,
                               List<String> engagementTypes, List<String> excludedCountries) {
    }

    public record SkillRequirement(Long skillId, String name, boolean mandatory, BigDecimal minYears) {
    }

    public record LanguageRequirement(String code, String level, boolean mandatory) {
    }

    public record JobInput(String eligibility, String eligibilityEvidence, String workModality, String country,
                           String seniority, BigDecimal minYearsExperience, List<SkillRequirement> skills,
                           boolean skillsFromAnalysis, List<LanguageRequirement> languages, BigDecimal salaryMinUsdMonth,
                           BigDecimal salaryMaxUsdMonth, String contractType, String engagementType) {
    }

    public record Dimension(String name, int score, String reason) {
    }

    public record Score(int total, Map<String, Dimension> dimensions, List<String> strengths, List<String> gaps,
                        List<String> missingMandatory, String classification, String recommendation) {
    }

    public static Score score(ProfileInput p, JobInput j) {
        Map<String, Dimension> d = new HashMap<>();
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // location / eligibility
        String elig = j.eligibility() == null ? "UNKNOWN" : j.eligibility();
        int loc = switch (elig) {
            case "ELIGIBLE" -> 100;
            case "LIKELY" -> 75;
            case "NOT_ELIGIBLE" -> 0;
            default -> 50;
        };
        String locReason = switch (elig) {
            case "ELIGIBLE" -> "Accesible desde Bolivia";
            case "LIKELY" -> "Probablemente accesible desde Bolivia";
            case "NOT_ELIGIBLE" -> "No accesible desde Bolivia";
            default -> "Elegibilidad desde Bolivia no confirmada";
        };
        if (j.eligibilityEvidence() != null) locReason += ": " + j.eligibilityEvidence();
        if (j.country() != null && p.excludedCountries().contains(j.country())) {
            loc = Math.min(loc, 10);
            locReason += " (país excluido en tus preferencias)";
        }
        d.put("location", new Dimension("location", loc, locReason));

        // skills
        List<SkillRequirement> mandatory = j.skills().stream().filter(SkillRequirement::mandatory).toList();
        List<SkillRequirement> optional = j.skills().stream().filter(s -> !s.mandatory()).toList();
        int skillScore;
        String skillReason;
        if (j.skills().isEmpty()) {
            skillScore = 50;
            skillReason = "La oferta no menciona tecnologías reconocibles";
        } else {
            double m = credit(mandatory, p, strengths, gaps, missing, true);
            double o = credit(optional, p, strengths, gaps, missing, false);
            if (!mandatory.isEmpty()) {
                skillScore = (int) Math.round(100 * (optional.isEmpty() ? m : 0.75 * m + 0.25 * o));
            } else {
                skillScore = (int) Math.round(100 * o);
            }
            long matched = j.skills().stream().filter(s -> s.skillId() != null && p.skills().containsKey(s.skillId())).count();
            skillReason = matched + " de " + j.skills().size() + " tecnologías " + (j.skillsFromAnalysis() ? "requeridas" : "mencionadas")
                    + " están en tu perfil" + (missing.isEmpty() ? "" : "; faltan obligatorias: " + String.join(", ", missing));
        }
        d.put("skills", new Dimension("skills", skillScore, skillReason));

        // experience (years only from employment dates)
        BigDecimal required = j.minYearsExperience() != null ? j.minYearsExperience() : switch (j.seniority() == null ? "" : j.seniority()) {
            case "JUNIOR" -> BigDecimal.ONE;
            case "MID" -> BigDecimal.valueOf(3);
            case "SENIOR" -> BigDecimal.valueOf(5);
            default -> null;
        };
        BigDecimal years = p.totalYears() == null ? BigDecimal.ZERO : p.totalYears();
        int exp;
        String expReason;
        if (required == null || required.signum() == 0) {
            exp = years.signum() > 0 ? 75 : 50;
            expReason = "La oferta no indica años de experiencia; tienes " + years + " años profesionales";
        } else {
            exp = (int) Math.min(100, Math.round(100 * years.doubleValue() / required.doubleValue()));
            expReason = "Requiere ~" + required.stripTrailingZeros().toPlainString() + " años; tu perfil muestra " + years
                    + " años profesionales (calculados desde tus fechas de empleo)";
            if (years.compareTo(required) >= 0) strengths.add("Cumples los años de experiencia requeridos (" + years + ")");
            else gaps.add("Experiencia profesional menor a la pedida (" + years + " de ~" + required.stripTrailingZeros().toPlainString() + " años)");
        }
        d.put("experience", new Dimension("experience", exp, expReason));

        // language
        int lang;
        String langReason;
        if (j.languages().isEmpty()) {
            lang = 80;
            langReason = "Sin requisito de idioma explícito";
        } else {
            double sum = 0;
            List<String> parts = new ArrayList<>();
            for (LanguageRequirement r : j.languages()) {
                String mine = p.languages().get(r.code());
                double s;
                if (mine == null) {
                    s = r.mandatory() ? 0 : 0.5;
                    parts.add(r.code().toUpperCase() + ": no figura en tu perfil");
                    if (r.mandatory()) gaps.add("Idioma requerido no declarado: " + r.code().toUpperCase());
                } else if (r.level() == null || LEVELS.indexOf(mine) >= LEVELS.indexOf(r.level())) {
                    s = 1;
                    parts.add(r.code().toUpperCase() + " " + mine + (r.level() == null ? "" : " ≥ " + r.level()));
                } else {
                    int diff = LEVELS.indexOf(r.level()) - LEVELS.indexOf(mine);
                    s = diff == 1 ? 0.6 : 0.2;
                    parts.add(r.code().toUpperCase() + " " + mine + " < " + r.level());
                    gaps.add("Nivel de " + r.code().toUpperCase() + " por debajo del pedido (" + mine + " vs " + r.level() + ")");
                }
                sum += s;
            }
            lang = (int) Math.round(100 * sum / j.languages().size());
            langReason = String.join("; ", parts);
        }
        d.put("language", new Dimension("language", lang, langReason));

        // seniority
        String mineLevel = p.preferredSeniority() != null ? p.preferredSeniority()
                : years.compareTo(BigDecimal.valueOf(2)) < 0 ? "JUNIOR" : years.compareTo(BigDecimal.valueOf(5)) < 0 ? "MID" : "SENIOR";
        int sen;
        String senReason;
        if (j.seniority() == null) {
            sen = 70;
            senReason = "La oferta no indica seniority";
        } else {
            List<String> order = List.of("JUNIOR", "MID", "SENIOR");
            int diff = order.indexOf(j.seniority()) - order.indexOf(mineLevel);
            sen = diff == 0 ? 100 : diff == 1 ? 50 : diff == -1 ? 80 : diff > 1 ? 10 : 60;
            senReason = "Oferta " + j.seniority() + " vs tu nivel " + mineLevel;
        }
        d.put("seniority", new Dimension("seniority", sen, senReason));

        // compensation
        int comp;
        String compReason;
        BigDecimal jobBest = j.salaryMaxUsdMonth() != null ? j.salaryMaxUsdMonth() : j.salaryMinUsdMonth();
        if (p.minSalaryUsdMonth() == null || jobBest == null) {
            comp = 70;
            compReason = jobBest == null ? "Salario no publicado (no penaliza)" : "No definiste un salario mínimo en tus preferencias";
        } else if (jobBest.compareTo(p.minSalaryUsdMonth()) >= 0) {
            comp = 100;
            compReason = "El salario publicado alcanza tu mínimo (~US$" + jobBest.toBigInteger() + "/mes)";
            strengths.add("Salario dentro de tus expectativas");
        } else {
            comp = (int) Math.round(100 * jobBest.doubleValue() / p.minSalaryUsdMonth().doubleValue());
            compReason = "Salario publicado (~US$" + jobBest.toBigInteger() + "/mes) por debajo de tu mínimo (US$"
                    + p.minSalaryUsdMonth().toBigInteger() + "/mes)";
            gaps.add("Salario por debajo de tu mínimo");
        }
        d.put("compensation", new Dimension("compensation", comp, compReason));

        // contract / engagement
        int con;
        String conReason;
        boolean noPrefs = p.contractTypes().isEmpty() && p.engagementTypes().isEmpty();
        boolean unknown = "UNKNOWN".equals(j.contractType()) && "UNKNOWN".equals(j.engagementType());
        if (noPrefs) {
            con = 80;
            conReason = "Sin preferencias de contrato definidas";
        } else if (unknown) {
            con = 70;
            conReason = "Tipo de contrato no indicado";
        } else if (p.contractTypes().contains(j.contractType()) || p.engagementTypes().contains(j.engagementType())) {
            con = 100;
            conReason = "Tipo de contrato acorde a tus preferencias";
        } else {
            con = 20;
            conReason = "Tipo de contrato (" + j.contractType() + " / " + j.engagementType() + ") fuera de tus preferencias";
        }
        d.put("contract", new Dimension("contract", con, conReason));

        int total = total(d);
        String classification = classify(elig, j.workModality(), total, missing.isEmpty(), false);
        return new Score(total, d, strengths, gaps, missing, classification, recommendation(classification));
    }

    private static double credit(List<SkillRequirement> reqs, ProfileInput p, List<String> strengths, List<String> gaps,
                                 List<String> missing, boolean mandatory) {
        if (reqs.isEmpty()) return 0;
        double sum = 0;
        for (SkillRequirement r : reqs) {
            SkillFact f = r.skillId() == null ? null : p.skills().get(r.skillId());
            if (f == null) {
                if (mandatory) {
                    missing.add(r.name());
                    gaps.add("Falta " + r.name() + " (obligatoria)");
                }
                continue;
            }
            double c = EVIDENCE.getOrDefault(f.evidence(), 0.2);
            if (r.minYears() != null && r.minYears().signum() > 0 && "PROFESSIONAL".equals(f.evidence())) {
                double ratio = f.years() == null ? 0 : f.years().doubleValue() / r.minYears().doubleValue();
                c *= Math.min(1.0, Math.max(0.3, ratio));
                if (ratio < 1) gaps.add(r.name() + ": " + f.years() + " años profesionales de " + r.minYears() + " pedidos");
            }
            if ("PROFESSIONAL".equals(f.evidence()) && mandatory) {
                strengths.add(r.name() + " con experiencia profesional" + (f.years() != null && f.years().signum() > 0 ? " (" + f.years() + " años)" : ""));
            } else if (!"PROFESSIONAL".equals(f.evidence()) && mandatory) {
                gaps.add(r.name() + " solo en " + switch (f.evidence()) {
                    case "PROJECT_ONLY" -> "proyectos";
                    case "ACADEMIC" -> "formación académica";
                    default -> "aprendizaje";
                });
            }
            sum += c;
        }
        return sum / reqs.size();
    }

    public static int total(Map<String, Dimension> d) {
        double sum = 0;
        for (var e : WEIGHTS.entrySet()) {
            Dimension dim = d.get(e.getKey());
            sum += (dim == null ? 50 : dim.score()) * e.getValue();
        }
        return (int) Math.round(sum / 100.0);
    }

    public static String classify(String eligibility, String modality, int total, boolean noMissingMandatory,
                                  boolean injectionSuspected) {
        if ("NOT_ELIGIBLE".equals(eligibility)) return "NOT_ELIGIBLE";
        if (injectionSuspected || ("UNKNOWN".equals(eligibility) && "REMOTE".equals(modality))) return "NEEDS_REVIEW";
        if (total >= 75 && noMissingMandatory) return "HIGH_MATCH";
        if (total >= 50) return "MEDIUM_MATCH";
        return "LOW_MATCH";
    }

    public static String recommendation(String classification) {
        return switch (classification) {
            case "HIGH_MATCH" -> "APPLY";
            case "MEDIUM_MATCH" -> "CONSIDER";
            case "NEEDS_REVIEW" -> "REVIEW";
            default -> "SKIP";
        };
    }

    public static final Set<String> CLASSIFICATIONS = Set.of("HIGH_MATCH", "MEDIUM_MATCH", "LOW_MATCH", "NOT_ELIGIBLE", "NEEDS_REVIEW");
}
