package com.jobhunter.integrations.infrastructure;

import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.integrations.domain.IntegrationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, Long> {

    Optional<IntegrationSetting> findByCode(IntegrationCode code);
}
