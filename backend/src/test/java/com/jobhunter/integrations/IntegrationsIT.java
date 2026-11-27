package com.jobhunter.integrations;

import com.jobhunter.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntegrationsIT extends IntegrationTest {

    @Test
    void adminPanelTogglesAndWriteOnlySecrets() throws Exception {
        String admin = register("admin@test.example");
        String user = register("user@test.example");

        mvc.perform(get("/api/v1/admin/integrations").header("Authorization", bearer(user))).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/integrations").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='APIFY')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.code=='REMOTIVE')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.code=='REMOTIVE')].requiresCredentials").value(false));

        // enabling without credentials is refused
        mvc.perform(put("/api/v1/admin/integrations/SERPAPI").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDENTIALS_REQUIRED"));

        String response = mvc.perform(put("/api/v1/admin/integrations/SERPAPI/secret").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("apiKey", "test-secret-key-1234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials[0].configured").value(true))
                .andExpect(jsonPath("$.credentials[0].last4").value("7890"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("test-secret-key");
        byte[] stored = jdbc.queryForObject("SELECT secret_ciphertext FROM integration_settings WHERE integration_code='SERPAPI'", byte[].class);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("test-secret-key");

        mvc.perform(put("/api/v1/admin/integrations/SERPAPI").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true, "dailyLimit", 10))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.dailyLimit").value(10));

        mvc.perform(delete("/api/v1/admin/integrations/SERPAPI/secret").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.credentials[0].configured").value(false));

        mvc.perform(get("/api/v1/sources").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$[?(@.code=='serpapi_google_jobs')].usable").value(false))
                .andExpect(jsonPath("$[?(@.code=='remotive')].usable").value(true));
    }

    @Test
    void apifyActorNeedsAssessmentBeforeEnabling() throws Exception {
        String admin = register("apify@test.example");
        String created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/apify/actors")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("actorId", "someone~test-actor", "displayName", "TEST DATA actor",
                                "inputTemplate", Map.of("keywords", "{{keywords}}", "maxItems", "{{maxItems}}"),
                                "fieldMapping", Map.of("title", "title", "sourceUrl", "url")))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = com.jobhunter.shared.json.Json.tree(created).get("id").asLong();
        mvc.perform(put("/api/v1/admin/apify/actors/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSESSMENT_REQUIRED"));
    }
}
