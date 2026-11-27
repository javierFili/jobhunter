package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.jobsources.domain.CompanyWatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyWatchRepository extends JpaRepository<CompanyWatch, Long> {

    List<CompanyWatch> findByUserIdOrderByAtsAscBoardTokenAsc(Long userId);

    List<CompanyWatch> findByUserIdAndAtsAndEnabledTrue(Long userId, String ats);

    Optional<CompanyWatch> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndAtsAndBoardToken(Long userId, String ats, String boardToken);
}
