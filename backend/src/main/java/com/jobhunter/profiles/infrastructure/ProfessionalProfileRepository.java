package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long> {

    List<ProfessionalProfile> findByUserId(Long userId);

    Optional<ProfessionalProfile> findByIdAndUserId(Long id, Long userId);

    List<ProfessionalProfile> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserId(Long userId);
}
