package com.jobhunter.matching.infrastructure;

import com.jobhunter.matching.domain.JobMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {

    Optional<JobMatch> findByUserIdAndProfileKeyAndJobOfferId(Long userId, Long profileKey, Long jobOfferId);

    List<JobMatch> findByUserIdAndProfileKeyAndJobOfferIdIn(Long userId, Long profileKey, Collection<Long> offerIds);

    @Query("""
            select m from JobMatch m, com.jobhunter.jobs.domain.JobOffer o
            where m.jobOfferId = o.id and o.status = 'ACTIVE' and m.userId = :userId and m.profileKey = :profileKey
              and m.classification in :classifications
            order by m.scoreTotal desc, m.id desc""")
    Page<JobMatch> recommended(Long userId, Long profileKey, Collection<String> classifications, Pageable pageable);

    @Query("select count(m) from JobMatch m where m.userId = :userId and m.classification = :classification")
    long countByClassification(Long userId, String classification);
}
