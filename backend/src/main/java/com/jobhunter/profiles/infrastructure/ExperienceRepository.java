package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByUserId(Long userId);

    Optional<Experience> findByIdAndUserId(Long id, Long userId);

    List<Experience> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserId(Long userId);
}
