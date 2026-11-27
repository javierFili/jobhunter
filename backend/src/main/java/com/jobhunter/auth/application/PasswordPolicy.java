package com.jobhunter.auth.application;

import com.jobhunter.shared.api.ApiException;

import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    private static final Set<String> COMMON = Set.of("1234567890", "password123", "qwertyuiop", "contraseña1",
            "contrasena1", "0123456789", "abcdefghij", "password1234", "12345678910", "iloveyou12");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.length() > 128) {
            throw ApiException.badRequest("WEAK_PASSWORD", "La contraseña no puede superar 128 caracteres");
        }
        if (COMMON.contains(password.toLowerCase())) {
            throw ApiException.badRequest("WEAK_PASSWORD", "La contraseña es demasiado común");
        }
    }
}
