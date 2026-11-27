package com.jobhunter.searches.application;

import com.jobhunter.integrations.application.IntegrationService;
import com.jobhunter.integrations.application.UsageService;
import com.jobhunter.integrations.domain.IntegrationCode;
import com.jobhunter.jobs.application.JobIngestionService;
import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.domain.JobSourceReference;
import com.jobhunter.jobs.infrastructure.JobSourceReferenceRepository;
import com.jobhunter.jobs.infrastructure.JobSourceRepository;
import com.jobhunter.jobsources.application.ConnectorRegistry;
import com.jobhunter.jobsources.application.SourceCatalogService;
import com.jobhunter.jobsources.domain.ApifyActor;
import com.jobhunter.jobsources.domain.CompanyWatch;
import com.jobhunter.jobsources.domain.FetchResult;
import com.jobhunter.jobsources.domain.JobSourceConnector;
import com.jobhunter.jobsources.domain.SourceQuery;
import com.jobhunter.jobsources.infrastructure.AdzunaConnector;
import com.jobhunter.jobsources.infrastructure.ApifyClient;
import com.jobhunter.jobsources.infrastructure.CompanyWatchRepository;
import com.jobhunter.notifications.application.NotificationService;
import com.jobhunter.searches.domain.JobSourceExecution;
import com.jobhunter.searches.domain.SavedSearch;
import com.jobhunter.searches.domain.SearchExecution;
import com.jobhunter.searches.infrastructure.JobSourceExecutionRepository;
import com.jobhunter.searches.infrastructure.SavedSearchRepository;
import com.jobhunter.searches.infrastructure.SearchExecutionRepository;
import com.jobhunter.searches.infrastructure.SearchResultStore;
import com.jobhunter.shared.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs a saved search: per source → availability/budget/interval checks → fetch → usage log → ingest (dedupe) →
 * local filters → new-job detection → notification. A failing source never aborts the others.
 */
@Service
public class SearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SearchOrchestrator.class);
    private static final Duration ATS_INTERVAL = Duration.ofHours(24);

    private final SavedSearchRepository searches;
    private final SearchExecutionRepository executions;
    private final JobSourceExecutionRepository sourceExecutions;
    private final SearchResultStore results;
    private final JobSourceRepository sources;
    private final JobSourceReferenceRepository references;
    private final ConnectorRegistry connectors;
    private final SourceCatalogService catalog;
    private final ApifyClient apify;
    private final CompanyWatchRepository watches;
    private final IntegrationService integrations;
    private final UsageService usage;
    private final JobIngestionService ingestion;
    private final JobQueryService jobs;
    private final NotificationService notifications;
    private final TaskExecutor executor;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SearchOrchestrator(SavedSearchRepository searches, SearchExecutionRepository executions,
                              JobSourceExecutionRepository sourceExecutions, SearchResultStore results,
                              JobSourceRepository sources, JobSourceReferenceRepository references,
                              ConnectorRegistry connectors, SourceCatalogService catalog, ApifyClient apify,
                              CompanyWatchRepository watches, IntegrationService integrations, UsageService usage,
                              JobIngestionService ingestion, JobQueryService jobs, NotificationService notifications,
                              @Qualifier("searchExecutor") TaskExecutor executor, ApplicationEventPublisher events,
                              PlatformTransactionManager txManager,
                              Clock clock) {
        this.searches = searches;
        this.executions = executions;
        this.sourceExecutions = sourceExecutions;
        this.results = results;
        this.sources = sources;
        this.references = references;
        this.connectors = connectors;
        this.catalog = catalog;
        this.apify = apify;
        this.watches = watches;
        this.integrations = integrations;
        this.usage = usage;
        this.ingestion = ingestion;
        this.jobs = jobs;
        this.notifications = notifications;
        this.executor = executor;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /** Starts a run asynchronously (bounded executor) and returns the execution id immediately. */
    public SearchExecution start(long userId, long searchId, String trigger) {
        SavedSearch search = searches.findByIdAndUserId(searchId, userId).orElseThrow(() -> ApiException.notFound("Búsqueda"));
        if (executions.existsBySavedSearchIdAndStatus(searchId, "RUNNING")) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RUNNING", "Esta búsqueda ya se está ejecutando");
        }
        SearchExecution execution = executions.save(new SearchExecution(searchId, userId, trigger, clock.instant()));
        tx.executeWithoutResult(s -> searches.findById(searchId).ifPresent(x -> x.markRun(clock.instant())));
        executor.execute(() -> {
            try {
                run(search, execution);
            } catch (RuntimeException e) {
                log.error("Search {} failed", searchId, e);
                execution.finish("FAILED", 0, 0, 0, BigDecimal.ZERO, e.getMessage(), clock.instant());
                executions.save(execution);
            }
        });
        return execution;
    }

    void run(SavedSearch search, SearchExecution execution) {
        long userId = search.getUserId();
        List<String> codes = search.getSourceCodes().isEmpty()
                ? catalog.list().stream().filter(SourceCatalogService.SourceView::usable).map(SourceCatalogService.SourceView::code).toList()
                : search.getSourceCodes();
        Set<Long> candidateIds = new LinkedHashSet<>();
        BigDecimal cost = BigDecimal.ZERO;
        int duplicates = 0;
        List<String> errors = new ArrayList<>();
        int failedSources = 0;
        for (String code : codes) {
            JobSource source = sources.findByCode(code).orElse(null);
            if (source == null) {
                errors.add(code + ": fuente desconocida");
                continue;
            }
            JobSourceExecution se = sourceExecutions.save(new JobSourceExecution(source.getId(), execution.getId(), userId, clock.instant()));
            try {
                SourceRun r = runSource(search, source);
                candidateIds.addAll(r.offerIds());
                cost = cost.add(r.cost());
                duplicates += r.duplicates();
                se.finish(r.status(), r.requests(), r.fetched(), r.fresh(), r.duplicates(), r.cost(), r.runId(), r.datasetId(),
                        null, r.message(), clock.instant());
                if ("FAILED".equals(r.status())) {
                    failedSources++;
                    errors.add(source.getDisplayName() + ": " + r.message());
                }
            } catch (RuntimeException e) {
                failedSources++;
                String message = e instanceof ApiException ae ? ae.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage();
                String status = e instanceof ApiException ae && ("BUDGET_EXCEEDED".equals(ae.code()) || "LIMIT_REACHED".equals(ae.code()))
                        ? "SKIPPED_BUDGET" : "FAILED";
                se.finish(status, 0, 0, 0, 0, BigDecimal.ZERO, null, null, status, message, clock.instant());
                errors.add(source.getDisplayName() + ": " + message);
                log.warn("Source {} failed in search {}: {}", code, search.getId(), message);
            }
            sourceExecutions.save(se);
        }

        List<Long> matching = jobs.matchingIds(userId, SearchService.toQuery(search), candidateIds);
        Set<Long> fresh = results.recordSeen(search.getId(), matching, clock.instant());
        results.recordExecutionJobs(execution.getId(), matching, fresh);
        String status = failedSources == 0 ? "SUCCEEDED" : (failedSources == codes.size() ? "FAILED" : "PARTIAL");
        execution.finish(status, matching.size(), fresh.size(), duplicates, cost, errors.isEmpty() ? null : String.join(" | ", errors), clock.instant());
        executions.save(execution);
        if (!matching.isEmpty()) {
            try {
                events.publishEvent(new com.jobhunter.searches.domain.SearchCompletedEvent(userId, search.getProfileId(), matching));
            } catch (RuntimeException e) {
                log.warn("Post-search matching failed: {}", e.getMessage());
            }
        }
        if (!fresh.isEmpty() && search.isNotifyOnNew()) {
            notifications.notify(userId, "NEW_JOBS", fresh.size() + " ofertas nuevas en «" + search.getName() + "»",
                    "La búsqueda encontró " + matching.size() + " ofertas, " + fresh.size() + " nuevas.",
                    "/searches/" + search.getId() + "/executions/" + execution.getId(),
                    Map.of("searchId", search.getId(), "executionId", execution.getId()));
        }
    }

    record SourceRun(String status, int requests, int fetched, int fresh, int duplicates, BigDecimal cost,
                     Set<Long> offerIds, String runId, String datasetId, String message) {
    }

    SourceRun runSource(SavedSearch search, JobSource source) {
        if (!source.isEnabled()) return skipped("SKIPPED_DISABLED", "Fuente desactivada");
        if (source.getCode().startsWith("apify:")) return runApify(search, source);
        JobSourceConnector connector = connectors.get(source.getCode()).orElseThrow(() -> new IllegalStateException("Sin conector"));
        IntegrationCode integration = connector.integration();
        if (!integrations.isUsable(integration)) {
            return skipped("SKIPPED_DISABLED", integration.displayName() + " desactivada o sin credenciales");
        }
        Map<String, String> creds = integrations.credentials(integration).orElseThrow();
        if (connector.capabilities().perCompany()) return runBoards(search, source, connector, creds);

        // whole-feed sources with strict rate rules: reuse the catalogue inside the minimum interval
        boolean feedSource = !connector.capabilities().keyword();
        if (feedSource && source.getLastFetchAt() != null
                && source.getLastFetchAt().plus(Duration.ofMinutes(source.getMinIntervalMinutes())).isAfter(clock.instant())) {
            Set<Long> cached = new HashSet<>();
            references.findActiveBySourceAndPrefix(source.getId(), "").forEach(r -> cached.add(r.getJobOfferId()));
            return new SourceRun("CACHED", 0, 0, 0, 0, BigDecimal.ZERO, cached, null, null,
                    "Se reutilizó la última descarga (intervalo mínimo " + source.getMinIntervalMinutes() + " min)");
        }

        SourceQuery query = query(search, connector, integration);
        int estimatedRequests = integration == IntegrationCode.SERPAPI
                ? Math.max(1, Math.min(2, Math.max(1, query.countries().size()))) * Math.max(1, (int) Math.ceil(query.maxResults() / 10.0))
                : 1;
        usage.checkBudget(integration, estimatedRequests, BigDecimal.ZERO);
        Instant started = clock.instant();
        FetchResult fetched;
        try {
            fetched = connector.fetch(query, creds);
        } catch (RuntimeException e) {
            usage.record(integration, UsageService.Usage.request(search.getUserId(), "SEARCH", 0, null, false,
                    Duration.between(started, clock.instant()).toMillis()));
            integrations.recordError(integration, e.getMessage());
            throw e;
        }
        usage.record(integration, new UsageService.Usage(search.getUserId(), "SEARCH", fetched.requests(), null, null, null,
                fetched.items().size(), fetched.costUsd(), fetched.costIsEstimate(), 200, true,
                (int) Duration.between(started, clock.instant()).toMillis(), null));
        integrations.recordSuccess(integration);
        SourceRun run = ingest(source, fetched);
        // Only a download that was actually stored may be reused as the cache of a whole-feed source.
        if (!"FAILED".equals(run.status())) {
            tx.executeWithoutResult(s -> sources.findById(source.getId()).ifPresent(x -> x.touchFetch(clock.instant())));
        }
        return run;
    }

    private SourceRun runBoards(SavedSearch search, JobSource source, JobSourceConnector connector, Map<String, String> creds) {
        List<CompanyWatch> boards = watches.findByUserIdAndAtsAndEnabledTrue(search.getUserId(), source.getCode().toUpperCase(Locale.ROOT));
        if (boards.isEmpty()) return skipped("SKIPPED_DISABLED", "No sigues ninguna empresa en " + source.getDisplayName());
        Set<Long> ids = new LinkedHashSet<>();
        int requests = 0, fetchedCount = 0, fresh = 0, dupes = 0, cachedBoards = 0;
        List<String> messages = new ArrayList<>();
        for (CompanyWatch w : boards) {
            String prefix = w.getBoardToken() + ":";
            boolean recent = w.getLastCheckedAt() != null && w.getLastCheckedAt().plus(ATS_INTERVAL).isAfter(clock.instant())
                    && "OK".equals(w.getLastStatus());
            if (recent) {
                references.findActiveBySourceAndPrefix(source.getId(), prefix).stream().map(JobSourceReference::getJobOfferId).forEach(ids::add);
                cachedBoards++;
                continue;
            }
            Instant started = clock.instant();
            try {
                usage.checkBudget(connector.integration(), 1, BigDecimal.ZERO);
                FetchResult r = connector.fetch(SourceQuery.board(w.getBoardToken(), w.getCompanyName(), 2000), creds);
                usage.record(connector.integration(), UsageService.Usage.request(search.getUserId(), "BOARD_FETCH",
                        r.items().size(), 200, true, Duration.between(started, clock.instant()).toMillis()));
                SourceRun run = ingest(source, r);
                Set<String> seen = new HashSet<>();
                r.items().forEach(d -> seen.add(JobIngestionService.externalId(d)));
                int removed = ingestion.markMissing(source, prefix, seen);
                if (removed > 0) messages.add(w.getBoardToken() + ": " + removed + " ofertas retiradas");
                ids.addAll(run.offerIds());
                requests += run.requests();
                fetchedCount += run.fetched();
                fresh += run.fresh();
                dupes += run.duplicates();
                markWatch(w.getId(), "OK", r.items().size());
            } catch (RuntimeException e) {
                markWatch(w.getId(), "ERROR", null);
                messages.add(w.getBoardToken() + ": " + e.getMessage());
            }
        }
        if (cachedBoards > 0) messages.add(cachedBoards + " empresas consultadas hace menos de 24 h (se reutilizaron)");
        return new SourceRun(requests == 0 && cachedBoards > 0 ? "CACHED" : "SUCCEEDED", requests, fetchedCount, fresh, dupes,
                BigDecimal.ZERO, ids, null, null, messages.isEmpty() ? null : String.join("; ", messages));
    }

    private void markWatch(Long watchId, String status, Integer count) {
        tx.executeWithoutResult(s -> watches.findById(watchId).ifPresent(w -> w.checked(clock.instant(), status, count)));
    }

    private SourceRun runApify(SavedSearch search, JobSource source) {
        ApifyActor actor = catalog.actorForSource(source.getCode());
        if (actor == null || !actor.isEnabled()) return skipped("SKIPPED_DISABLED", "Actor desactivado");
        if (!integrations.isUsable(IntegrationCode.APIFY)) return skipped("SKIPPED_DISABLED", "Apify está desactivado");
        String token = integrations.credentials(IntegrationCode.APIFY).orElseThrow().get("token");
        usage.checkBudget(IntegrationCode.APIFY, 1, actor.getMaxChargeUsd());
        ApifyClient.Limits limits = apify.limits(token);
        if (limits.maxMonthlyUsageUsd() != null && limits.monthlyUsageUsd() != null
                && limits.monthlyUsageUsd().add(actor.getMaxChargeUsd()).compareTo(limits.maxMonthlyUsageUsd()) > 0) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "BUDGET_EXCEEDED", "Límite mensual de la cuenta de Apify alcanzado");
        }
        Instant started = clock.instant();
        SourceQuery q = new SourceQuery(search.getKeywords(), search.filters().countries(), false,
                search.filters().postedWithinDays(), search.getMaxResultsPerSource(), null, null);
        FetchResult r = apify.run(actor, q, token);
        usage.record(IntegrationCode.APIFY, new UsageService.Usage(search.getUserId(), "ACTOR_RUN", r.requests(), null, null, null,
                r.items().size(), r.costUsd(), true, 200, true, (int) Duration.between(started, clock.instant()).toMillis(), r.externalRunId()));
        SourceRun run = ingest(source, r);
        return new SourceRun(r.warnings().isEmpty() ? "SUCCEEDED" : "FAILED", run.requests(), run.fetched(), run.fresh(),
                run.duplicates(), r.costUsd(), run.offerIds(), r.externalRunId(), r.externalDatasetId(),
                r.warnings().isEmpty() ? null : String.join("; ", r.warnings()));
    }

    private SourceRun ingest(JobSource source, FetchResult fetched) {
        var result = ingestion.ingest(source, fetched.items());
        Set<Long> ids = new LinkedHashSet<>();
        result.items().forEach(i -> ids.add(i.offerId()));
        int fresh = (int) result.count(JobIngestionService.Outcome.NEW);
        int dupes = (int) result.count(JobIngestionService.Outcome.MERGED_DUPLICATE);
        String msg = result.failed() > 0 ? result.failed() + " elementos no se pudieron procesar" : null;
        boolean allFailed = !fetched.items().isEmpty() && result.failed() >= fetched.items().size();
        return new SourceRun(allFailed ? "FAILED" : "SUCCEEDED", fetched.requests(), fetched.items().size(), fresh, dupes, fetched.costUsd(), ids,
                fetched.externalRunId(), fetched.externalDatasetId(), msg);
    }

    private SourceQuery query(SavedSearch search, JobSourceConnector connector, IntegrationCode integration) {
        var f = search.filters();
        List<String> countries = f.countries();
        if (integration == IntegrationCode.ADZUNA) {
            List<String> supported = countries.stream().map(c -> c.toLowerCase(Locale.ROOT))
                    .filter(AdzunaConnector.COUNTRIES::contains).toList();
            if (supported.isEmpty()) {
                String def = integrations.config(IntegrationCode.ADZUNA).path("defaultCountry").asString("us");
                supported = List.of(def);
            }
            countries = supported;
        }
        boolean remoteOnly = f.modalities().size() == 1 && "REMOTE".equals(f.modalities().get(0));
        int max = search.getMaxResultsPerSource();
        if (integration == IntegrationCode.SERPAPI) {
            int pages = integrations.config(IntegrationCode.SERPAPI).path("maxPagesPerSearch").asInt(2);
            max = Math.min(max, Math.max(1, pages) * 10);
        }
        return new SourceQuery(search.getKeywords(), countries, remoteOnly, f.postedWithinDays(), max, null, null);
    }

    private static SourceRun skipped(String status, String message) {
        return new SourceRun(status, 0, 0, 0, 0, BigDecimal.ZERO, Set.of(), null, null, message);
    }
}
