package com.jobhunter.resumes;

import com.jobhunter.resumes.domain.DateRanges;
import com.jobhunter.resumes.domain.HeuristicResumeParser;
import com.jobhunter.resumes.domain.ParsedResume;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicResumeParserTest {

    /** TEST DATA — fictitious person and companies. */
    private static final String CV = """
            Ana Prueba Ficticia
            ana.prueba@example.com | +591 70000000
            github.com/ana-test | linkedin.com/in/ana-test

            PERFIL PROFESIONAL
            Desarrolladora backend con experiencia en Java y Spring Boot.

            EXPERIENCIA LABORAL
            Backend Developer
            TEST DATA Example Corp
            Ene 2022 – Actualidad
            • Diseñé APIs REST con Spring Boot y MySQL
            • Reduje el tiempo de respuesta en un 30%
            Desarrolladora Laravel | TEST DATA Otra SRL
            03/2020 - 12/2021
            - Mantenimiento de sistemas en PHP y Laravel

            EDUCACIÓN
            Universidad Mayor de San Simón
            Ingeniería de Sistemas
            2015 - 2020

            HABILIDADES
            Java, Spring Boot, Laravel, MySQL, Git

            IDIOMAS
            Español: nativo
            Inglés: B2
            """;

    private final HeuristicResumeParser parser = new HeuristicResumeParser(text -> {
        // tiny fake dictionary for the unit test
        List<ParsedResume.SkillItem> found = new java.util.ArrayList<>();
        String[][] known = {{"1", "Java"}, {"2", "Spring Boot"}, {"3", "Laravel"}, {"4", "MySQL"}, {"5", "Git"}, {"6", "PHP"}};
        for (String[] k : known) {
            if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(k[1]) + "\\b").matcher(text).find()) {
                found.add(new ParsedResume.SkillItem(Long.parseLong(k[0]), k[1]));
            }
        }
        return found;
    });

    @Test
    void parsesContactAndSummary() {
        ParsedResume r = parser.parse(CV);
        assertThat(r.contact().fullName()).isEqualTo("Ana Prueba Ficticia");
        assertThat(r.contact().email()).isEqualTo("ana.prueba@example.com");
        assertThat(r.contact().phone()).contains("70000000");
        assertThat(r.contact().githubUrl()).isEqualTo("https://github.com/ana-test");
        assertThat(r.contact().linkedinUrl()).isEqualTo("https://linkedin.com/in/ana-test");
        assertThat(r.summary()).startsWith("Desarrolladora backend");
    }

    @Test
    void parsesExperienceBlocksWithDates() {
        ParsedResume r = parser.parse(CV);
        assertThat(r.experiences()).hasSize(2);
        ParsedResume.ExperienceItem first = r.experiences().get(0);
        assertThat(first.positionTitle()).isEqualTo("Backend Developer");
        assertThat(first.companyName()).isEqualTo("TEST DATA Example Corp");
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(first.current()).isTrue();
        assertThat(first.achievements()).hasSize(2);

        ParsedResume.ExperienceItem second = r.experiences().get(1);
        assertThat(second.positionTitle()).isEqualTo("Desarrolladora Laravel");
        assertThat(second.companyName()).isEqualTo("TEST DATA Otra SRL");
        assertThat(second.endDate()).isEqualTo(LocalDate.of(2021, 12, 1));
        assertThat(second.skillIds()).contains(3L, 6L);
    }

    @Test
    void parsesEducationSkillsAndLanguages() {
        ParsedResume r = parser.parse(CV);
        assertThat(r.educations()).hasSize(1);
        assertThat(r.educations().get(0).institution()).isEqualTo("Universidad Mayor de San Simón");
        assertThat(r.educations().get(0).degree()).isEqualTo("Ingeniería de Sistemas");
        assertThat(r.skills()).extracting(ParsedResume.SkillItem::name)
                .containsExactlyInAnyOrder("Java", "Spring Boot", "Laravel", "MySQL", "Git");
        assertThat(r.languages()).extracting(ParsedResume.LanguageItem::languageCode, ParsedResume.LanguageItem::level)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("es", "NATIVE"),
                        org.assertj.core.groups.Tuple.tuple("en", "B2"));
    }

    @Test
    void dateRangesInSeveralLanguages() {
        assertThat(DateRanges.find("Jan 2019 - Present").orElseThrow().current()).isTrue();
        assertThat(DateRanges.find("gennaio 2018 – dicembre 2019").orElseThrow().end()).isEqualTo(LocalDate.of(2019, 12, 1));
        assertThat(DateRanges.find("März 2020 bis heute").orElseThrow().start()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(DateRanges.find("2017 to 2018").orElseThrow().start()).isEqualTo(LocalDate.of(2017, 1, 1));
        assertThat(DateRanges.find("Teléfono 2020")).isEmpty();
        assertThat(DateRanges.find("2021 - 2019")).isEmpty();
    }
}
