package com.jobhunter.searches.infrastructure;

import com.jobhunter.searches.domain.JobSourceExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobSourceExecutionRepository extends JpaRepository<JobSourceExecution, Long> {

    List<JobSourceExecution> findBySearchExecutionIdOrderByStartedAtAsc(Long searchExecutionId);
}
