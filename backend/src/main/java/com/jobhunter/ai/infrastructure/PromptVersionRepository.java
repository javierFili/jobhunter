package com.jobhunter.ai.infrastructure;

import com.jobhunter.ai.domain.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    Optional<PromptVersion> findByPromptKeyAndActiveTrue(String promptKey);

    List<PromptVersion> findByPromptKeyOrderByVersionDesc(String promptKey);

    Optional<PromptVersion> findByPromptKeyAndVersion(String promptKey, int version);

    Optional<PromptVersion> findByPromptKeyAndChecksum(String promptKey, String checksum);

    @Query("select coalesce(max(p.version), 0) from PromptVersion p where p.promptKey = :key")
    int maxVersion(String key);
}
