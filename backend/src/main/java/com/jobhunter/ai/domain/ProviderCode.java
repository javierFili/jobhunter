package com.jobhunter.ai.domain;

import com.jobhunter.integrations.domain.IntegrationCode;

public enum ProviderCode {
    DEEPSEEK(IntegrationCode.DEEPSEEK, "deepseek"),
    CLAUDE(IntegrationCode.CLAUDE, "claude");

    private final IntegrationCode integration;
    /** Name used in user consents. */
    private final String consentName;

    ProviderCode(IntegrationCode integration, String consentName) {
        this.integration = integration;
        this.consentName = consentName;
    }

    public IntegrationCode integration() { return integration; }
    public String consentName() { return consentName; }
}
