package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSourceRepository extends JpaRepository<JobSource, Long> {

    Optional<JobSource> findByCode(String code);
}
