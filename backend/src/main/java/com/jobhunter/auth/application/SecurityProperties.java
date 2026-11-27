package com.jobhunter.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "jobhunter.security")
public record SecurityProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
                                 boolean cookieSecure, List<String> corsOrigins) {

    public SecurityProperties {
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(14);
        if (corsOrigins == null) corsOrigins = List.of();
    }
}
