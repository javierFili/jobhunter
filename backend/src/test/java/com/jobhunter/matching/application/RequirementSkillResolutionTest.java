package com.jobhunter.matching.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A requirement only maps to a catalogue skill when the dictionary hit IS the technology, not part of another tool. */
class RequirementSkillResolutionTest {

    @Test
    void acceptsTheTechnologyWithFillerWordsOrVersions() {
        assertThat(JobAnalysisService.isWholeTechnology("RESTful API", "RESTful")).isTrue();
        assertThat(JobAnalysisService.isWholeTechnology("Java 21", "Java")).isTrue();
        assertThat(JobAnalysisService.isWholeTechnology("Spring Boot 3.x", "Spring Boot")).isTrue();
        assertThat(JobAnalysisService.isWholeTechnology("Experiencia sólida en Laravel", "Laravel")).isTrue();
    }

    @Test
    void rejectsADifferentToolThatContainsAKnownName() {
        assertThat(JobAnalysisService.isWholeTechnology("Laravel Dusk", "Laravel")).isFalse();
        assertThat(JobAnalysisService.isWholeTechnology("Laravel Query Builder", "Laravel")).isFalse();
        assertThat(JobAnalysisService.isWholeTechnology("Laravel Authentication", "Laravel")).isFalse();
        assertThat(JobAnalysisService.isWholeTechnology("Spring Security", "Spring")).isFalse();
    }
}
