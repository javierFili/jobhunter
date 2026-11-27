package com.jobhunter.resumes.infrastructure;

import com.jobhunter.resumes.domain.ResumeExtraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeExtractionRepository extends JpaRepository<ResumeExtraction, Long> {

    List<ResumeExtraction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ResumeExtraction> findByIdAndUserId(Long id, Long userId);
}
