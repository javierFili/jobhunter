package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FxRateRepository extends JpaRepository<FxRate, FxRate.Id> {

    /** Most recent rate of every currency. */
    @Query("select f from FxRate f where f.id.asOf = (select max(g.id.asOf) from FxRate g where g.id.currency = f.id.currency)")
    List<FxRate> latest();
}
