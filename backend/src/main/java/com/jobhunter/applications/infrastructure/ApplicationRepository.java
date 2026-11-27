package com.jobhunter.applications.infrastructure;

import com.jobhunter.applications.domain.Application;
import com.jobhunter.applications.domain.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    Optional<Application> findByIdAndUserId(Long id, Long userId);

    Optional<Application> findFirstByUserIdAndJobOfferId(Long userId, Long jobOfferId);

    List<Application> findByUserIdOrderByKanbanOrderAscUpdatedAtDesc(Long userId);

    List<Application> findByUserIdAndStatus(Long userId, ApplicationStatus status);

    @Query("select a from Application a where a.followUpAt is not null and a.followUpAt <= :today "
            + "and (a.followUpNotifiedOn is null or a.followUpNotifiedOn < :today) "
            + "and a.status not in (com.jobhunter.applications.domain.ApplicationStatus.REJECTED, "
            + "com.jobhunter.applications.domain.ApplicationStatus.WITHDRAWN)")
    List<Application> dueFollowUps(LocalDate today);

    @Query("select a.status as status, count(a) as count from Application a where a.userId = :userId "
            + "and a.createdAt >= :from and a.createdAt < :to group by a.status")
    List<StatusCount> countByStatus(Long userId, Instant from, Instant to);

    interface StatusCount {
        ApplicationStatus getStatus();
        Long getCount();
    }

    Page<Application> findByUserId(Long userId, Pageable pageable);
}
