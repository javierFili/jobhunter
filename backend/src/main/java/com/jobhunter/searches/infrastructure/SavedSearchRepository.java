package com.jobhunter.searches.infrastructure;

import com.jobhunter.searches.domain.SavedSearch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByNameAsc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Query("select s from SavedSearch s where s.enabled = true and s.nextRunAt is not null and s.nextRunAt <= :now order by s.nextRunAt")
    List<SavedSearch> findDue(Instant now, Pageable pageable);
}
