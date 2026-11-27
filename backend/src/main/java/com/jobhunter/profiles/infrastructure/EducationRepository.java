package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByUserId(Long userId);

    Optional<Education> findByIdAndUserId(Long id, Long userId);

    List<Education> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserId(Long userId);
}
