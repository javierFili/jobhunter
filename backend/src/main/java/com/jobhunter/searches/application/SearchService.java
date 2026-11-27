package com.jobhunter.searches.application;

import com.jobhunter.jobs.application.JobQuery;
import com.jobhunter.searches.domain.SavedSearch;
import com.jobhunter.searches.domain.SearchFilters;
import com.jobhunter.searches.infrastructure.SavedSearchRepository;
import com.jobhunter.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class SearchService {

    static final int MAX_SEARCHES = 30;
    static final int MAX_SCHEDULED = 10;

    private final SavedSearchRepository repository;
    private final Clock clock;

    public SearchService(SavedSearchRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record SavedSearchRequest(
            @NotBlank @Size(max = 120) String name,
            Long profileId,
            @Size(max = 300) String keywords,
            @Valid SearchFilters filters,
            @NotNull @Size(max = 30) List<@NotBlank @Size(max = 60) String> sourceCodes,
            @NotNull SavedSearch.ScheduleType scheduleType,
            @Min(60) @Max(10080) Integer intervalMinutes,
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String dailyTime,
            @Size(max = 64) String timezone,
            boolean enabled,
            @Min(1) @Max(100) Integer maxResultsPerSource,
            boolean notifyOnNew,
            Integer version) {
    }

    @Transactional(readOnly = true)
    public List<SavedSearch> list(long userId) {
        return repository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public SavedSearch get(long userId, long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Búsqueda"));
    }

    @Transactional
    public SavedSearch create(long userId, SavedSearchRequest r) {
        if (repository.countByUserId(userId) >= MAX_SEARCHES) {
            throw ApiException.badRequest("LIMIT_REACHED", "Máximo " + MAX_SEARCHES + " búsquedas guardadas");
        }
        SavedSearch s = new SavedSearch(userId);
        apply(userId, s, r);
        return repository.save(s);
    }

    @Transactional
    public SavedSearch update(long userId, long id, SavedSearchRequest r) {
        SavedSearch s = get(userId, id);
        s.checkVersion(r.version());
        apply(userId, s, r);
        return s;
    }

    @Transactional
    public void delete(long userId, long id) {
        get(userId, id).markDeleted(clock.instant());
    }

    private void apply(long userId, SavedSearch s, SavedSearchRequest r) {
        String tz = r.timezone() == null || r.timezone().isBlank() ? "America/La_Paz" : r.timezone();
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Zona horaria no válida");
        }
        if (r.scheduleType() == SavedSearch.ScheduleType.INTERVAL && r.intervalMinutes() == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Indica el intervalo en minutos (mínimo 60)");
        }
        if (r.scheduleType() != SavedSearch.ScheduleType.MANUAL && r.enabled()) {
            long scheduled = repository.findByUserIdOrderByNameAsc(userId).stream()
                    .filter(x -> !x.equals(s) && x.isEnabled() && !"MANUAL".equals(x.getScheduleType())).count();
            if (scheduled >= MAX_SCHEDULED) {
                throw ApiException.badRequest("LIMIT_REACHED", "Máximo " + MAX_SCHEDULED + " búsquedas programadas activas");
            }
        }
        if (repository.findByUserIdOrderByNameAsc(userId).stream()
                .anyMatch(x -> !x.equals(s) && x.getName().equalsIgnoreCase(r.name().trim()))) {
            throw ApiException.conflict("Ya existe una búsqueda con ese nombre");
        }
        s.update(r.name().trim(), r.profileId(), r.keywords() == null || r.keywords().isBlank() ? null : r.keywords().trim(),
                r.filters() == null ? SearchFilters.empty() : r.filters(), r.sourceCodes().stream().distinct().toList(),
                r.scheduleType(), r.intervalMinutes(), r.dailyTime() == null ? null : LocalTime.parse(r.dailyTime()), tz,
                r.enabled(), r.maxResultsPerSource() == null ? 50 : r.maxResultsPerSource(), r.notifyOnNew(), clock.instant());
    }

    /** Saved-search filters → catalogue query (keywords + typed filters), ACTIVE jobs, hidden ones excluded. */
    public static JobQuery toQuery(SavedSearch s) {
        SearchFilters f = s.filters();
        return new JobQuery(s.getKeywords(), f.skillIds(), f.countries(), f.regions(), f.modalities(), f.remoteScopes(),
                f.eligibility(), f.contractTypes(), f.engagementTypes(), f.seniority(), f.salaryMinUsdMonth(),
                f.languages(), f.postedWithinDays(), f.companies(), f.excludeCompanies(), List.of(), null, false,
                "ACTIVE", "recent");
    }
}
