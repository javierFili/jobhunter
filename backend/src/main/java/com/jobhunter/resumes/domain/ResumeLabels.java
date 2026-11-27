package com.jobhunter.resumes.domain;

import java.util.Map;

/** Section headings and fixed words for the 5 supported output languages. */
public final class ResumeLabels {

    public static final java.util.List<String> LANGUAGES = java.util.List.of("es", "en", "it", "de", "fr");

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            "es", Map.ofEntries(Map.entry("summary", "Perfil profesional"), Map.entry("experience", "Experiencia laboral"),
                    Map.entry("projects", "Proyectos"), Map.entry("education", "Educación"),
                    Map.entry("certifications", "Certificaciones"), Map.entry("skills", "Habilidades técnicas"),
                    Map.entry("languages", "Idiomas"), Map.entry("present", "Actualidad"),
                    Map.entry("PROGRAMMING_LANGUAGE", "Lenguajes"), Map.entry("FRAMEWORK", "Frameworks y librerías"),
                    Map.entry("DATABASE", "Bases de datos"), Map.entry("CLOUD", "Nube"), Map.entry("TOOL", "Herramientas"),
                    Map.entry("METHODOLOGY", "Metodologías"), Map.entry("OTHER", "Otros"),
                    Map.entry("PERSONAL", "Proyecto personal"), Map.entry("ACADEMIC", "Proyecto académico"),
                    Map.entry("FREELANCE_CLIENT", "Proyecto freelance"), Map.entry("OPEN_SOURCE", "Código abierto"),
                    Map.entry("NATIVE", "Nativo")),
            "en", Map.ofEntries(Map.entry("summary", "Professional Summary"), Map.entry("experience", "Work Experience"),
                    Map.entry("projects", "Projects"), Map.entry("education", "Education"),
                    Map.entry("certifications", "Certifications"), Map.entry("skills", "Technical Skills"),
                    Map.entry("languages", "Languages"), Map.entry("present", "Present"),
                    Map.entry("PROGRAMMING_LANGUAGE", "Languages"), Map.entry("FRAMEWORK", "Frameworks & Libraries"),
                    Map.entry("DATABASE", "Databases"), Map.entry("CLOUD", "Cloud"), Map.entry("TOOL", "Tools"),
                    Map.entry("METHODOLOGY", "Methodologies"), Map.entry("OTHER", "Other"),
                    Map.entry("PERSONAL", "Personal project"), Map.entry("ACADEMIC", "Academic project"),
                    Map.entry("FREELANCE_CLIENT", "Freelance project"), Map.entry("OPEN_SOURCE", "Open source"),
                    Map.entry("NATIVE", "Native")),
            "it", Map.ofEntries(Map.entry("summary", "Profilo professionale"), Map.entry("experience", "Esperienza lavorativa"),
                    Map.entry("projects", "Progetti"), Map.entry("education", "Istruzione"),
                    Map.entry("certifications", "Certificazioni"), Map.entry("skills", "Competenze tecniche"),
                    Map.entry("languages", "Lingue"), Map.entry("present", "Presente"),
                    Map.entry("PROGRAMMING_LANGUAGE", "Linguaggi"), Map.entry("FRAMEWORK", "Framework e librerie"),
                    Map.entry("DATABASE", "Database"), Map.entry("CLOUD", "Cloud"), Map.entry("TOOL", "Strumenti"),
                    Map.entry("METHODOLOGY", "Metodologie"), Map.entry("OTHER", "Altro"),
                    Map.entry("PERSONAL", "Progetto personale"), Map.entry("ACADEMIC", "Progetto accademico"),
                    Map.entry("FREELANCE_CLIENT", "Progetto freelance"), Map.entry("OPEN_SOURCE", "Open source"),
                    Map.entry("NATIVE", "Madrelingua")),
            "de", Map.ofEntries(Map.entry("summary", "Profil"), Map.entry("experience", "Berufserfahrung"),
                    Map.entry("projects", "Projekte"), Map.entry("education", "Ausbildung"),
                    Map.entry("certifications", "Zertifikate"), Map.entry("skills", "Technische Kenntnisse"),
                    Map.entry("languages", "Sprachen"), Map.entry("present", "heute"),
                    Map.entry("PROGRAMMING_LANGUAGE", "Programmiersprachen"), Map.entry("FRAMEWORK", "Frameworks & Bibliotheken"),
                    Map.entry("DATABASE", "Datenbanken"), Map.entry("CLOUD", "Cloud"), Map.entry("TOOL", "Werkzeuge"),
                    Map.entry("METHODOLOGY", "Methoden"), Map.entry("OTHER", "Sonstiges"),
                    Map.entry("PERSONAL", "Persönliches Projekt"), Map.entry("ACADEMIC", "Akademisches Projekt"),
                    Map.entry("FREELANCE_CLIENT", "Freelance-Projekt"), Map.entry("OPEN_SOURCE", "Open Source"),
                    Map.entry("NATIVE", "Muttersprache")),
            "fr", Map.ofEntries(Map.entry("summary", "Profil professionnel"), Map.entry("experience", "Expérience professionnelle"),
                    Map.entry("projects", "Projets"), Map.entry("education", "Formation"),
                    Map.entry("certifications", "Certifications"), Map.entry("skills", "Compétences techniques"),
                    Map.entry("languages", "Langues"), Map.entry("present", "Aujourd'hui"),
                    Map.entry("PROGRAMMING_LANGUAGE", "Langages"), Map.entry("FRAMEWORK", "Frameworks et bibliothèques"),
                    Map.entry("DATABASE", "Bases de données"), Map.entry("CLOUD", "Cloud"), Map.entry("TOOL", "Outils"),
                    Map.entry("METHODOLOGY", "Méthodologies"), Map.entry("OTHER", "Autres"),
                    Map.entry("PERSONAL", "Projet personnel"), Map.entry("ACADEMIC", "Projet académique"),
                    Map.entry("FREELANCE_CLIENT", "Projet freelance"), Map.entry("OPEN_SOURCE", "Open source"),
                    Map.entry("NATIVE", "Langue maternelle")));

    private static final Map<String, Map<String, String>> LANGUAGE_NAMES = Map.of(
            "es", Map.of("es", "Español", "en", "Inglés", "it", "Italiano", "de", "Alemán", "fr", "Francés", "pt", "Portugués", "qu", "Quechua", "ay", "Aymara"),
            "en", Map.of("es", "Spanish", "en", "English", "it", "Italian", "de", "German", "fr", "French", "pt", "Portuguese", "qu", "Quechua", "ay", "Aymara"),
            "it", Map.of("es", "Spagnolo", "en", "Inglese", "it", "Italiano", "de", "Tedesco", "fr", "Francese", "pt", "Portoghese", "qu", "Quechua", "ay", "Aymara"),
            "de", Map.of("es", "Spanisch", "en", "Englisch", "it", "Italienisch", "de", "Deutsch", "fr", "Französisch", "pt", "Portugiesisch", "qu", "Quechua", "ay", "Aymara"),
            "fr", Map.of("es", "Espagnol", "en", "Anglais", "it", "Italien", "de", "Allemand", "fr", "Français", "pt", "Portugais", "qu", "Quechua", "ay", "Aymara"));

    private ResumeLabels() {
    }

    public static String get(String language, String key) {
        return LABELS.getOrDefault(language, LABELS.get("en")).getOrDefault(key, key);
    }

    public static Map<String, String> all(String language) {
        return LABELS.getOrDefault(language, LABELS.get("en"));
    }

    public static String languageName(String outputLanguage, String code) {
        return LANGUAGE_NAMES.getOrDefault(outputLanguage, LANGUAGE_NAMES.get("en")).getOrDefault(code, code.toUpperCase());
    }
}
