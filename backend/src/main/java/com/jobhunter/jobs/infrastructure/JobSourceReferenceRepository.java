package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.JobSourceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobSourceReferenceRepository extends JpaRepository<JobSourceReference, Long> {

    Optional<JobSourceReference> findByJobSourceIdAndExternalId(Long jobSourceId, String externalId);

    List<JobSourceReference> findByJobOfferId(Long jobOfferId);

    List<JobSourceReference> findByJobOfferIdIn(Collection<Long> jobOfferIds);

    @Query("select r from JobSourceReference r where r.jobSourceId = :sourceId and r.removedAt is null and r.externalId like concat(:prefix, '%')")
    List<JobSourceReference> findActiveBySourceAndPrefix(Long sourceId, String prefix);

    @Query("select count(r) from JobSourceReference r where r.jobOfferId = :offerId and r.removedAt is null")
    long countActiveForOffer(Long offerId);
}
