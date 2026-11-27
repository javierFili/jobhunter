package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByUserId(Long userId);

    Optional<Project> findByIdAndUserId(Long id, Long userId);

    List<Project> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserId(Long userId);
}
