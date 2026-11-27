package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.JobPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPreferencesRepository extends JpaRepository<JobPreferences, Long> {

    Optional<JobPreferences> findByUserId(Long userId);
}
