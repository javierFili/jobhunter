package com.jobhunter.profiles.application;

import com.jobhunter.profiles.domain.Enums.SkillCategory;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.infrastructure.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillDictionaryTest {

    private static long nextId = 1;

    private static Skill skill(String name, SkillCategory cat, String... aliases) {
        Skill s = new Skill(name, cat);
        ReflectionTestUtils.setField(s, "id", nextId++);
        s.getAliases().addAll(List.of(aliases));
        return s;
    }

    private final SkillDictionary dictionary;

    SkillDictionaryTest() {
        SkillRepository repo = mock(SkillRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                skill("Java", SkillCategory.PROGRAMMING_LANGUAGE),
                skill("JavaScript", SkillCategory.PROGRAMMING_LANGUAGE, "js"),
                skill("C", SkillCategory.PROGRAMMING_LANGUAGE),
                skill("C++", SkillCategory.PROGRAMMING_LANGUAGE, "cpp"),
                skill("C#", SkillCategory.PROGRAMMING_LANGUAGE, "csharp"),
                skill("Go", SkillCategory.PROGRAMMING_LANGUAGE, "golang"),
                skill(".NET", SkillCategory.FRAMEWORK, "dotnet", "asp.net core"),
                skill("Node.js", SkillCategory.FRAMEWORK, "node", "nodejs"),
                skill("Spring Boot", SkillCategory.FRAMEWORK, "springboot"),
                skill("Laravel", SkillCategory.FRAMEWORK)));
        dictionary = new SkillDictionary(repo);
    }

    private List<String> names(String text) {
        return dictionary.find(text).stream().map(SkillDictionary.Match::name).toList();
    }

    @Test
    void javaIsNotFoundInsideJavaScript() {
        assertThat(names("Experience with JavaScript and TypeScript")).containsExactly("JavaScript");
    }

    @Test
    void symbolsAreRespected() {
        assertThat(names("We use C++, C# and .NET 8")).containsExactlyInAnyOrder("C++", "C#", ".NET");
    }

    @Test
    void ambiguousShortNamesNeedListContext() {
        assertThat(names("Let's go to the meeting, I can't")).isEmpty();
        assertThat(names("Stack: Java, Go, C")).containsExactly("Java", "Go", "C");
        assertThat(names("Backend in Golang")).containsExactly("Go");
    }

    @Test
    void multiWordAndAliasesMatch() {
        assertThat(names("Spring  Boot microservices with node and Laravel."))
                .containsExactly("Spring Boot", "Node.js", "Laravel");
    }

    @Test
    void resultsAreInOrderOfAppearance() {
        assertThat(names("Laravel first, then Java")).containsExactly("Laravel", "Java");
    }
}
