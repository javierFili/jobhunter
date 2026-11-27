package com.jobhunter.shared;

import com.jobhunter.shared.crypto.SecretCipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    // TEST DATA ONLY — fixed 32-byte key
    private final SecretCipher cipher = new SecretCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void roundTrips() {
        byte[] blob = cipher.encrypt("sk-test-123", "DEEPSEEK|system");
        assertThat(cipher.decrypt(blob, "DEEPSEEK|system")).isEqualTo("sk-test-123");
    }

    @Test
    void ciphertextIsRandomized() {
        assertThat(cipher.encrypt("same", "a")).isNotEqualTo(cipher.encrypt("same", "a"));
    }

    @Test
    void ciphertextIsBoundToItsOwner() {
        byte[] blob = cipher.encrypt("sk-test-123", "DEEPSEEK|system");
        assertThatThrownBy(() -> cipher.decrypt(blob, "CLAUDE|system")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notConfiguredWithoutKey() {
        assertThat(new SecretCipher("").isConfigured()).isFalse();
    }
}
