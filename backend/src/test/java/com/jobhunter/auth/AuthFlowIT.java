package com.jobhunter.auth;

import com.jobhunter.shared.json.Json;
import com.jobhunter.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends IntegrationTest {

    @Test
    void firstUserIsAdminAndSecondIsUser() throws Exception {
        String admin = register("admin@test.example");
        String user = register("user@test.example");
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("dup@test.example");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "DUP@test.example", "password", "test-password-123",
                                "fullName", "TEST DATA"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void wrongPasswordGivesGenericError() throws Exception {
        register("login@test.example");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "login@test.example", "password", "wrong-password-1"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@test.example", "password", "wrong-password-1"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "rt@test.example", "password", "test-password-123",
                                "fullName", "TEST DATA"))))
                .andExpect(status().isCreated()).andReturn();
        Cookie first = login.getResponse().getCookie("jh_refresh");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andReturn();
        Cookie second = refreshed.getResponse().getCookie("jh_refresh");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // re-using the rotated token = theft signal → 401 and the newer token is revoked too
        mvc.perform(post("/api/v1/auth/refresh").cookie(first).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresCsrfHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("jh_refresh", "x")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MISSING_CSRF_HEADER"));
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consentsDefaultToNotGrantedAndCanBeToggled() throws Exception {
        String token = register("consent@test.example");
        mvc.perform(get("/api/v1/me/consents").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].granted").value(false));
        String body = Json.write(java.util.List.of(Map.of("consentType", "AI_PROCESS_CV", "provider", "claude", "granted", true)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/me/consents")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.consentType=='AI_PROCESS_CV' && @.provider=='claude')].granted").value(true));
    }
}
