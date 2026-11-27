package com.jobhunter.integrations.infrastructure;

import com.jobhunter.integrations.domain.ApiUsageLog;
import com.jobhunter.integrations.domain.IntegrationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    @Query("select coalesce(sum(u.requestCount), 0) from ApiUsageLog u where u.code = :code and u.occurredAt >= :since")
    long requestsSince(IntegrationCode code, Instant since);

    @Query("select coalesce(sum(u.costUsd), 0) from ApiUsageLog u where u.code = :code and u.occurredAt >= :since")
    BigDecimal costSince(IntegrationCode code, Instant since);

    @Query("""
            select u.code as code, sum(u.requestCount) as requests, sum(u.costUsd) as cost,
                   coalesce(sum(u.inputTokens), 0) as inputTokens, coalesce(sum(u.outputTokens), 0) as outputTokens,
                   coalesce(sum(u.cachedTokens), 0) as cachedTokens
            from ApiUsageLog u where u.occurredAt >= :from and u.occurredAt < :to group by u.code""")
    List<UsageAggregate> aggregate(Instant from, Instant to);

    interface UsageAggregate {
        IntegrationCode getCode();
        Long getRequests();
        BigDecimal getCost();
        Long getInputTokens();
        Long getOutputTokens();
        Long getCachedTokens();
    }
}
