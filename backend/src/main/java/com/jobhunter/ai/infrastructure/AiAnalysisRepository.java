package com.jobhunter.ai.infrastructure;

import com.jobhunter.ai.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findFirstByCacheKeyAndStatusOrderByIdDesc(String cacheKey, AiAnalysis.Status status);

    Optional<AiAnalysis> findFirstBySubjectTypeAndSubjectIdAndPromptKeyOrderByIdDesc(String subjectType, Long subjectId, String promptKey);

    Optional<AiAnalysis> findFirstBySubjectTypeAndSubjectIdAndPromptKeyAndUserIdOrderByIdDesc(String subjectType, Long subjectId,
                                                                                              String promptKey, Long userId);
}
