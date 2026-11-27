package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long>, JpaSpecificationExecutor<JobOffer> {

    List<JobOffer> findByFingerprint(byte[] fingerprint);

    List<JobOffer> findByCanonicalUrlHash(byte[] canonicalUrlHash);

    @Query("select count(o) from JobOffer o where o.status = 'ACTIVE'")
    long countActive();

    @Query("select count(o) from JobOffer o where o.status = 'ACTIVE' and o.firstSeenAt >= :since")
    long countNewSince(Instant since);

    @Query("select count(o) from JobOffer o where o.status = 'ACTIVE' and o.boliviaEligibility in ('ELIGIBLE','LIKELY')")
    long countEligibleOrLikely();

    /** Search-API results are not complete listings: expire offers not seen for a long time. */
    @Modifying
    @Query("update JobOffer o set o.status = 'EXPIRED' where o.status = 'ACTIVE' and (o.lastSeenAt < :cutoff or (o.expiresAt is not null and o.expiresAt < :now))")
    int expireStale(Instant cutoff, Instant now);
}
