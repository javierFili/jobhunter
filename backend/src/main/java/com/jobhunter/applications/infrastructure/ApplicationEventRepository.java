package com.jobhunter.applications.infrastructure;

import com.jobhunter.applications.domain.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    List<ApplicationEvent> findByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);

    /** How many of the user's applications ever reached each status (funnel), counting status-change events. */
    @Query("""
            select e.toStatus as status, count(distinct e.applicationId) as count from ApplicationEvent e
            where e.toStatus is not null and e.occurredAt >= :from and e.occurredAt < :to
              and e.applicationId in (select a.id from com.jobhunter.applications.domain.Application a where a.userId = :userId)
            group by e.toStatus""")
    List<Reached> reached(Long userId, Instant from, Instant to);

    interface Reached {
        String getStatus();
        Long getCount();
    }
}
