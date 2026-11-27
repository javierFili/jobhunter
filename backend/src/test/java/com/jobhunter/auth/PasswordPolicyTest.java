package com.jobhunter.auth;

import com.jobhunter.auth.application.PasswordPolicy;
import com.jobhunter.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsCommonPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("1234567890")).isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse-battery")).doesNotThrowAnyException();
    }
}
