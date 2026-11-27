package com.jobhunter.applications;

import com.jobhunter.applications.domain.Application;
import com.jobhunter.applications.domain.ApplicationStatus;
import com.jobhunter.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTest {

    private static Application app() {
        return new Application(1L, null, "TEST DATA Co", "Developer", null, null);
    }

    @Test
    void appliedOnlyThroughMarkSubmitted() {
        Application a = app();
        assertThatThrownBy(() -> a.transition(ApplicationStatus.APPLIED)).isInstanceOf(ApiException.class);
        a.transition(ApplicationStatus.READY_TO_APPLY);
        a.markSubmitted(Application.Method.EMAIL, Instant.now());
        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(a.getAppliedAt()).isNotNull();
        assertThatThrownBy(() -> a.markSubmitted(Application.Method.EMAIL, Instant.now())).isInstanceOf(ApiException.class);
    }

    @Test
    void terminalStatesAreFinal() {
        Application a = app();
        a.transition(ApplicationStatus.WITHDRAWN);
        assertThatThrownBy(() -> a.transition(ApplicationStatus.DISCOVERED)).isInstanceOf(ApiException.class);
        assertThat(ApplicationStatus.REJECTED.allowedTargets()).isEmpty();
    }

    @Test
    void postApplicationFlow() {
        Application a = app();
        a.markSubmitted(Application.Method.LINKEDIN, Instant.now());
        a.transition(ApplicationStatus.INTERVIEW);
        a.transition(ApplicationStatus.OFFER);
        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.OFFER);
        assertThatThrownBy(() -> a.transition(ApplicationStatus.READY_TO_APPLY)).isInstanceOf(ApiException.class);
    }
}
