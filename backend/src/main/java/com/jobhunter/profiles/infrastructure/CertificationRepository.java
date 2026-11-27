package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.Certification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    List<Certification> findByUserId(Long userId);

    Optional<Certification> findByIdAndUserId(Long id, Long userId);

    List<Certification> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserId(Long userId);
}
