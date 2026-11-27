package com.jobhunter.ai.infrastructure;

import com.jobhunter.ai.domain.TaskRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRouteRepository extends JpaRepository<TaskRoute, Long> {

    Optional<TaskRoute> findByPromptKey(String promptKey);
}
