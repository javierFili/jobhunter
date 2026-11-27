package com.jobhunter.support;

import com.jobhunter.shared.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for integration tests against the local MySQL schema {@code jobhunter_test} (see scripts/db-setup.sql).
 * Every test starts from an empty set of users; the skills catalogue (seed data) is kept.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        // users cascade to every user-owned table; non-cascading leftovers are removed explicitly
        for (String table : TestTables.EXTRA_CLEANUP) {
            jdbc.update(table);
        }
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM audit_logs");
    }

    /** Registers a user (TEST DATA) and returns its access token. */
    protected String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("email", email, "password", "test-password-123",
                                "fullName", "TEST DATA User"))))
                .andExpect(status().isCreated())
                .andReturn();
        return Json.tree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String json(Object value) {
        return Json.write(value);
    }
}
