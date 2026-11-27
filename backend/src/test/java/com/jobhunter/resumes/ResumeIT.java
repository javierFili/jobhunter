package com.jobhunter.resumes;

import com.jobhunter.shared.json.Json;
import com.jobhunter.support.IntegrationTest;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeIT extends IntegrationTest {

    /** TEST DATA — synthetic DOCX CV of a fictitious person. */
    static byte[] sampleDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : List.of("Ana Prueba Ficticia", "ana.prueba@example.com", "EXPERIENCIA",
                    "Backend Developer", "TEST DATA Example Corp", "Ene 2022 - Actualidad",
                    "• APIs REST con Java y Spring Boot", "HABILIDADES", "Java, Spring Boot, MySQL",
                    "IDIOMAS", "Inglés B2")) {
                doc.createParagraph().createRun().setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void uploadParseAndImportAsUnverified() throws Exception {
        String token = register("cv@test.example");
        MockMultipartFile file = new MockMultipartFile("file", "test-cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sampleDocx());
        String body = mvc.perform(multipart("/api/v1/resumes/uploads").file(file).header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parsed.contact.email").value("ana.prueba@example.com"))
                .andExpect(jsonPath("$.parsed.experiences[0].companyName").value("TEST DATA Example Corp"))
                .andReturn().getResponse().getContentAsString();
        JsonNode upload = Json.tree(body);
        long uploadId = upload.get("id").asLong();
        List<Long> skillIds = new java.util.ArrayList<>();
        upload.get("parsed").get("skills").forEach(s -> skillIds.add(s.get("skillId").asLong()));
        assertThat(skillIds).hasSizeGreaterThanOrEqualTo(3);

        Map<String, Object> experience = Map.of("companyName", "TEST DATA Example Corp", "positionTitle",
                "Backend Developer", "employmentType", "FULL_TIME", "startDate", "2022-01-01", "current", true);
        mvc.perform(post("/api/v1/resumes/uploads/" + uploadId + "/import").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("experiences", List.of(experience), "skillIds", skillIds,
                                "languages", List.of(Map.of("languageCode", "en", "level", "B2"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.experiences").value(1));

        mvc.perform(get("/api/v1/experiences").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].verified").value(false)).andExpect(jsonPath("$[0].source").value("CV_IMPORT"));
        mvc.perform(get("/api/v1/languages").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].verified").value(false));

        // unverified facts are not usable for resumes
        mvc.perform(post("/api/v1/resumes").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "CV prueba", "language", "es", "templateId", "classic"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROFILE_EMPTY"));
    }

    @Test
    void rejectsFakePdf() throws Exception {
        String token = register("fake@test.example");
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "not a pdf".getBytes());
        mvc.perform(multipart("/api/v1/resumes/uploads").file(file).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILE"));
        MockMultipartFile exe = new MockMultipartFile("file", "cv.exe", "application/octet-stream", new byte[]{1, 2});
        mvc.perform(multipart("/api/v1/resumes/uploads").file(exe).header("Authorization", bearer(token)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void resumeLifecycleFromVerifiedProfile() throws Exception {
        String token = register("life@test.example");
        long java = jdbc.queryForObject("SELECT id FROM skills WHERE normalized_name='java'", Long.class);
        mvc.perform(post("/api/v1/profiles").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Java Backend", "headline", "Backend Developer",
                        "contactEmail", "ana@example.com")))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/experiences").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("companyName", "TEST DATA Example Corp", "positionTitle", "Backend Developer",
                        "employmentType", "FULL_TIME", "startDate", "2022-01-01", "current", true,
                        "achievements", List.of("Diseñé APIs REST"), "skillIds", List.of(java)))))
                .andExpect(status().isCreated());

        String created = mvc.perform(post("/api/v1/resumes").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "CV Java", "language", "en", "templateId", "modern"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version.versionNo").value(1))
                .andReturn().getResponse().getContentAsString();
        long resumeId = Json.tree(created).get("resume").get("id").asLong();

        String v1 = mvc.perform(get("/api/v1/resumes/" + resumeId + "/versions/1").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content.experience[0].bullets[0].sourceRefs[0]").exists())
                .andExpect(jsonPath("$.content.basics.email").value("ana@example.com"))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = Json.tree(v1).get("content");

        // edit → new version 2
        tools.jackson.databind.node.ObjectNode edited = (tools.jackson.databind.node.ObjectNode) content.deepCopy();
        edited.put("summary", "Backend developer focused on Java");
        mvc.perform(post("/api/v1/resumes/" + resumeId + "/versions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("content", edited, "baseVersionNo", 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(2));

        mvc.perform(get("/api/v1/resumes/" + resumeId + "/compare?from=1&to=2").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[?(@.op=='ADDED')].text").value("Backend developer focused on Java"));

        mvc.perform(post("/api/v1/resumes/" + resumeId + "/versions/2/approve").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.hasPdf").value(true));

        byte[] pdf = mvc.perform(get("/api/v1/resumes/" + resumeId + "/versions/2/pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // another user cannot read it
        String other = register("other@test.example");
        mvc.perform(get("/api/v1/resumes/" + resumeId + "/versions/2/pdf").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }
}
