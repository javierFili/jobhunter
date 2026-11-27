package com.jobhunter.profiles;

import com.jobhunter.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIT extends IntegrationTest {

    private long skillId(String name) {
        return jdbc.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", Long.class, name);
    }

    private Map<String, Object> experience(long... skillIds) {
        Map<String, Object> e = new HashMap<>();
        e.put("companyName", "TEST DATA — Example Corp");
        e.put("positionTitle", "Backend Developer");
        e.put("employmentType", "FULL_TIME");
        e.put("startDate", "2022-01-01");
        e.put("endDate", "2024-01-01");
        e.put("current", false);
        e.put("achievements", List.of("Built REST APIs"));
        e.put("skillIds", java.util.Arrays.stream(skillIds).boxed().toList());
        return e;
    }

    @Test
    void experienceCreatesProfessionalSkillsWithComputedYears() throws Exception {
        String token = register("exp@test.example");
        long java = skillId("java");
        mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(experience(java))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.verified").value(true));
        mvc.perform(get("/api/v1/skills").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evidenceLevel").value("PROFESSIONAL"))
                .andExpect(jsonPath("$[0].yearsProfessional").value(2.0));
    }

    @Test
    void personalProjectNeverCountsAsProfessional() throws Exception {
        String token = register("prj@test.example");
        long laravel = skillId("laravel");
        Map<String, Object> project = Map.of("kind", "PERSONAL", "name", "TEST DATA side project",
                "skillIds", List.of(laravel));
        mvc.perform(post("/api/v1/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(project)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("PERSONAL"));
        mvc.perform(get("/api/v1/skills").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].evidenceLevel").value("PROJECT_ONLY"))
                .andExpect(jsonPath("$[0].yearsProfessional").value(0));
    }

    @Test
    void invalidDatesAreRejected() throws Exception {
        String token = register("dates@test.example");
        Map<String, Object> bad = experience();
        bad.put("startDate", "2024-01-01");
        bad.put("endDate", "2020-01-01");
        mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void usersCannotSeeOrEditEachOthersData() throws Exception {
        String alice = register("alice@test.example");
        String bob = register("bob@test.example");
        String body = mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(json(experience())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = com.jobhunter.shared.json.Json.tree(body).get("id").asLong();

        mvc.perform(get("/api/v1/experiences/" + id).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/experiences/" + id).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(json(experience())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/experiences/" + id).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/experiences").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String token = register("version@test.example");
        String body = mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(experience())))
                .andReturn().getResponse().getContentAsString();
        long id = com.jobhunter.shared.json.Json.tree(body).get("id").asLong();
        Map<String, Object> update = experience();
        update.put("version", 0);
        update.put("positionTitle", "Senior Backend Developer");
        mvc.perform(put("/api/v1/experiences/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(update))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/experiences/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isConflict());
    }

    @Test
    void profilesLanguagesPreferencesAndCompleteness() throws Exception {
        String token = register("full@test.example");
        mvc.perform(post("/api/v1/profiles").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Java Backend", "headline", "Backend Developer"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(put("/api/v1/languages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(Map.of("languageCode", "es", "level", "NATIVE"),
                                Map.of("languageCode", "en", "level", "B2")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/api/v1/preferences").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("regions", List.of("LATAM", "WORLDWIDE"), "modalities", List.of("REMOTE"),
                                "countriesInclude", List.of("BO"), "relocationOk", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regions[0]").value("LATAM"));
        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleteness").value(greaterThan(0)))
                .andExpect(jsonPath("$.counts.languages").value(2));
    }

    @Test
    void catalogSearchFindsByPrefixAndAlias() throws Exception {
        String token = register("cat@test.example");
        mvc.perform(get("/api/v1/catalog/skills?q=spring").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Spring"));
    }
}
