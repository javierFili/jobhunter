package com.jobhunter.scheduler;

import com.jobhunter.jobs.infrastructure.JobOfferRepository;
import com.jobhunter.searches.application.SearchOrchestrator;
import com.jobhunter.searches.domain.SavedSearch;
import com.jobhunter.searches.infrastructure.SavedSearchRepository;
import com.jobhunter.searches.infrastructure.SearchExecutionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * One dispatcher tick per minute picks due saved searches (next_run_at ≤ now) — at most 2 per tick — instead of
 * registering one scheduled task per search (bounded, survives restarts because state lives in MySQL).
 */
@Component
@ConditionalOnProperty(name = "jobhunter.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduleDispatcher.class);
    static final int MAX_PER_TICK = 2;

    private final SavedSearchRepository searches;
    private final SearchExecutionRepository executions;
    private final SearchOrchestrator orchestrator;
    private final JobOfferRepository offers;
    private final Clock clock;
    private final com.jobhunter.applications.application.ApplicationService applications;

    public ScheduleDispatcher(SavedSearchRepository searches, SearchExecutionRepository executions,
                              SearchOrchestrator orchestrator, JobOfferRepository offers, Clock clock,
                              com.jobhunter.applications.application.ApplicationService applications) {
        this.applications = applications;
        this.searches = searches;
        this.executions = executions;
        this.orchestrator = orchestrator;
        this.offers = offers;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${jobhunter.scheduler.tick-ms:60000}", initialDelay = 30_000)
    @SchedulerLock(name = "search-dispatcher", lockAtMostFor = "PT5M")
    public void tick() {
        for (SavedSearch s : searches.findDue(clock.instant(), PageRequest.of(0, MAX_PER_TICK))) {
            try {
                orchestrator.start(s.getUserId(), s.getId(), "SCHEDULED");
            } catch (RuntimeException e) {
                log.warn("Could not start scheduled search {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    /** Nightly: offers not seen for 30 days (or past their expiry date) become EXPIRED. */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @SchedulerLock(name = "job-expiry", lockAtMostFor = "PT30M")
    @Transactional
    public void expireStaleJobs() {
        int n = offers.expireStale(clock.instant().minus(Duration.ofDays(30)), clock.instant());
        if (n > 0) log.info("Expired {} job offers", n);
    }

    /** Daily follow-up reminders for applications (13:00 UTC = 09:00 in Bolivia). */
    @Scheduled(cron = "0 0 13 * * *", zone = "UTC")
    @SchedulerLock(name = "follow-ups", lockAtMostFor = "PT10M")
    public void followUps() {
        int n = applications.notifyFollowUps(java.time.LocalDate.now(clock));
        if (n > 0) log.info("Sent {} follow-up reminders", n);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedRuns() {
        executions.failStale(clock.instant().minus(Duration.ofMinutes(1)), clock.instant());
    }
}
