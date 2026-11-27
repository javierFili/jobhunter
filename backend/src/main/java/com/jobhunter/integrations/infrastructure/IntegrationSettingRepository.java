package com.jobhunter.integrations.infrastructure;

import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.integrations.domain.IntegrationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Optional;

public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, Long> {

    Optional<IntegrationSetting> findByCode(IntegrationCode code);

    /*
     * Health telemetry is written with plain UPDATEs that do not bump the optimistic-lock version: concurrent calls
     * (parallel AI tasks, searches) must not fail with a version conflict, and an admin editing the settings at the
     * same time must not get a spurious 409.
     */
    @Modifying
    @Query(value = "UPDATE integration_settings SET last_success_at = :now, status = IF(enabled, 'OK', status) "
            + "WHERE integration_code = :code", nativeQuery = true)
    int markSuccess(@Param("code") String code, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE integration_settings SET last_error_at = :now, last_error = :message, "
            + "status = IF(enabled, 'ERROR', status) WHERE integration_code = :code", nativeQuery = true)
    int markError(@Param("code") String code, @Param("now") Instant now, @Param("message") String message);
}
