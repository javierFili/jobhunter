package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {

    List<JobRequirement> findByJobOfferId(Long jobOfferId);

    @Modifying
    @Query("delete from JobRequirement r where r.jobOfferId = :offerId")
    void deleteByJobOfferId(Long offerId);
}
