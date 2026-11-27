package com.jobhunter.searches.domain;

import com.jobhunter.shared.domain.VersionedEntity;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.json.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saved_searches")
@SQLRestriction("deleted_at is null")
public class SavedSearch extends VersionedEntity {

    public enum ScheduleType { MANUAL, INTERVAL, DAILY }

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "profile_id") private Long profileId;
    @Column(length = 300) private String keywords;
    @Column(nullable = false, columnDefinition = "json") private String filters;
    @Convert(converter = StringListConverter.class)
    @Column(name = "source_codes", nullable = false, columnDefinition = "json") private List<String> sourceCodes = new ArrayList<>();
    @Column(name = "schedule_type", nullable = false, length = 16) private String scheduleType = "MANUAL";
    @Column(name = "interval_minutes") private Integer intervalMinutes;
    @Column(name = "daily_time") private LocalTime dailyTime;
    @Column(nullable = false, length = 64) private String timezone = "America/La_Paz";
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "next_run_at") private Instant nextRunAt;
    @Column(name = "last_run_at") private Instant lastRunAt;
    @Column(name = "max_results_per_source", nullable = false) private int maxResultsPerSource = 50;
    @Column(name = "notify_on_new", nullable = false) private boolean notifyOnNew = true;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected SavedSearch() {
    }

    public SavedSearch(Long userId) {
        this.userId = userId;
    }

    public void update(String name, Long profileId, String keywords, SearchFilters filters, List<String> sourceCodes,
                       ScheduleType scheduleType, Integer intervalMinutes, LocalTime dailyTime, String timezone,
                       boolean enabled, int maxResultsPerSource, boolean notifyOnNew, Instant now) {
        this.name = name;
        this.profileId = profileId;
        this.keywords = keywords;
        this.filters = Json.write(filters.normalized());
        this.sourceCodes = new ArrayList<>(sourceCodes);
        this.scheduleType = scheduleType.name();
        this.intervalMinutes = scheduleType == ScheduleType.INTERVAL ? intervalMinutes : null;
        this.dailyTime = scheduleType == ScheduleType.DAILY ? dailyTime : null;
        this.timezone = timezone;
        this.enabled = enabled;
        this.maxResultsPerSource = maxResultsPerSource;
        this.notifyOnNew = notifyOnNew;
        this.nextRunAt = computeNextRun(now);
    }

    /** Next execution time; null for MANUAL or disabled searches. */
    public Instant computeNextRun(Instant from) {
        if (!enabled) return null;
        return switch (ScheduleType.valueOf(scheduleType)) {
            case MANUAL -> null;
            case INTERVAL -> from.plusSeconds(60L * Math.max(60, intervalMinutes == null ? 1440 : intervalMinutes));
            case DAILY -> {
                ZoneId zone = ZoneId.of(timezone);
                ZonedDateTime now = from.atZone(zone);
                ZonedDateTime candidate = now.with(dailyTime == null ? LocalTime.of(8, 0) : dailyTime).withSecond(0).withNano(0);
                if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
                yield candidate.toInstant();
            }
        };
    }

    public void markRun(Instant now) {
        this.lastRunAt = now;
        this.nextRunAt = computeNextRun(now);
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
        this.enabled = false;
        this.nextRunAt = null;
    }

    public SearchFilters filters() {
        SearchFilters f = Json.read(filters, SearchFilters.class);
        return f == null ? SearchFilters.empty() : f.normalized();
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Long getProfileId() { return profileId; }
    public String getKeywords() { return keywords; }
    public List<String> getSourceCodes() { return sourceCodes; }
    public String getScheduleType() { return scheduleType; }
    public Integer getIntervalMinutes() { return intervalMinutes; }
    public LocalTime getDailyTime() { return dailyTime; }
    public String getTimezone() { return timezone; }
    public boolean isEnabled() { return enabled; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public int getMaxResultsPerSource() { return maxResultsPerSource; }
    public boolean isNotifyOnNew() { return notifyOnNew; }
}
