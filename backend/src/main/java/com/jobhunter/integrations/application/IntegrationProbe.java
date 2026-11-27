package com.jobhunter.integrations.application;

import com.jobhunter.integrations.domain.IntegrationCode;

import java.util.Map;

/** Cheapest possible connectivity check of an integration ("Probar conexión"). */
public interface IntegrationProbe {

    IntegrationCode code();

    ProbeResult probe(Map<String, String> credentials);

    record ProbeResult(boolean ok, String message, Map<String, Object> details) {

        public static ProbeResult ok(String message, Map<String, Object> details) {
            return new ProbeResult(true, message, details);
        }

        public static ProbeResult fail(String message) {
            return new ProbeResult(false, message, Map.of());
        }
    }
}
