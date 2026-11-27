package com.jobhunter.jobsources.api;

import com.jobhunter.jobs.application.FxService;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobsources.application.SourceCatalogService;
import com.jobhunter.jobsources.application.WatchlistService;
import com.jobhunter.jobsources.domain.ApifyActor;
import com.jobhunter.jobsources.domain.CompanyWatch;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
public class SourcesController {

    private final SourceCatalogService catalog;
    private final WatchlistService watchlist;
    private final FxService fx;
    private final Clock clock;

    public SourcesController(SourceCatalogService catalog, WatchlistService watchlist, FxService fx, Clock clock) {
        this.catalog = catalog;
        this.watchlist = watchlist;
        this.fx = fx;
        this.clock = clock;
    }

    // ---- user: sources & watchlist ----

    @GetMapping("/api/v1/sources")
    public List<SourceCatalogService.SourceView> sources() {
        return catalog.list();
    }

    public record WatchDto(Long id, String ats, String boardToken, String companyName, boolean enabled,
                           Instant lastCheckedAt, String lastStatus, Integer lastJobCount) {
        static WatchDto from(CompanyWatch w) {
            return new WatchDto(w.getId(), w.getAts(), w.getBoardToken(), w.getCompanyName(), w.isEnabled(),
                    w.getLastCheckedAt(), w.getLastStatus(), w.getLastJobCount());
        }
    }

    public record WatchRequest(String ats, String boardToken, String companyName) {
    }

    public record WatchUpdate(Boolean enabled, String companyName) {
    }

    @GetMapping("/api/v1/watchlist")
    public List<WatchDto> watchlist() {
        return watchlist.list(CurrentUser.id()).stream().map(WatchDto::from).toList();
    }

    @PostMapping("/api/v1/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    public WatchDto addWatch(@RequestBody WatchRequest body) {
        return WatchDto.from(watchlist.add(CurrentUser.id(), body.ats(), body.boardToken(), body.companyName()));
    }

    @PutMapping("/api/v1/watchlist/{id}")
    public WatchDto updateWatch(@PathVariable long id, @RequestBody WatchUpdate body) {
        return WatchDto.from(watchlist.update(CurrentUser.id(), id, body.enabled(), body.companyName()));
    }

    @DeleteMapping("/api/v1/watchlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWatch(@PathVariable long id) {
        watchlist.delete(CurrentUser.id(), id);
    }

    @PostMapping("/api/v1/watchlist/validate")
    public WatchlistService.Validation validate(@RequestBody WatchRequest body) {
        CurrentUser.id();
        return watchlist.check(body.ats(), body.boardToken());
    }

    // ---- admin: sources, Apify actors, FX rates ----

    public record AdminSourceDto(String code, String type, String displayName, boolean enabled, String termsUrl,
                                 String termsNotes, String attributionText, boolean attributionRequired,
                                 boolean commercialUseAllowed, int minIntervalMinutes, Integer dailyRequestCap,
                                 Instant lastFetchAt) {
        static AdminSourceDto from(JobSource s) {
            return new AdminSourceDto(s.getCode(), s.getType(), s.getDisplayName(), s.isEnabled(), s.getTermsUrl(),
                    s.getTermsNotes(), s.getAttributionText(), s.isAttributionRequired(), s.isCommercialUseAllowed(),
                    s.getMinIntervalMinutes(), s.getDailyRequestCap(), s.getLastFetchAt());
        }
    }

    public record SourceUpdate(Boolean enabled, Integer minIntervalMinutes, Integer dailyRequestCap) {
    }

    @GetMapping("/api/v1/admin/sources")
    public List<AdminSourceDto> adminSources() {
        return catalog.adminList().stream().map(AdminSourceDto::from).toList();
    }

    @PutMapping("/api/v1/admin/sources/{code}")
    public AdminSourceDto updateSource(@PathVariable String code, @RequestBody SourceUpdate body) {
        return AdminSourceDto.from(catalog.updateSource(CurrentUser.id(), code, body.enabled(), body.minIntervalMinutes(),
                body.dailyRequestCap()));
    }

    public record ActorDto(Long id, String actorId, String displayName, String platform, boolean enabled,
                           JsonNode inputTemplate, JsonNode fieldMapping, int maxItems, BigDecimal maxChargeUsd,
                           int timeoutSecs, Integer memoryMb, String pricingModel, String assessmentNotes,
                           Instant assessedAt, String sourceCode, int version) {
        static ActorDto from(ApifyActor a) {
            return new ActorDto(a.getId(), a.getActorId(), a.getDisplayName(), a.getPlatform(), a.isEnabled(),
                    Json.tree(a.getInputTemplate()), Json.tree(a.getFieldMapping()), a.getMaxItems(), a.getMaxChargeUsd(),
                    a.getTimeoutSecs(), a.getMemoryMb(), a.getPricingModel(), a.getAssessmentNotes(), a.getAssessedAt(),
                    a.sourceCode(), a.getVersion());
        }
    }

    @GetMapping("/api/v1/admin/apify/actors")
    public List<ActorDto> actors() {
        return catalog.actors().stream().map(ActorDto::from).toList();
    }

    @PostMapping("/api/v1/admin/apify/actors")
    @ResponseStatus(HttpStatus.CREATED)
    public ActorDto createActor(@RequestBody SourceCatalogService.ActorCommand body) {
        return ActorDto.from(catalog.createActor(CurrentUser.id(), body));
    }

    @PutMapping("/api/v1/admin/apify/actors/{id}")
    public ActorDto updateActor(@PathVariable long id, @RequestBody SourceCatalogService.ActorCommand body) {
        return ActorDto.from(catalog.updateActor(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/api/v1/admin/apify/actors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActor(@PathVariable long id) {
        catalog.deleteActor(CurrentUser.id(), id);
    }

    public record FxDto(String currency, BigDecimal rateToUsd, LocalDate asOf) {
    }

    public record FxRequest(@NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
                            @NotNull @DecimalMin(value = "0.00000001") BigDecimal rateToUsd) {
    }

    @GetMapping("/api/v1/admin/fx-rates")
    public List<FxDto> fxRates() {
        return fx.latest().stream().map(r -> new FxDto(r.getId().currency(), r.getRateToUsd(), r.getId().asOf())).toList();
    }

    @PutMapping("/api/v1/admin/fx-rates")
    public List<FxDto> setFxRates(@RequestBody List<@Valid FxRequest> body) {
        LocalDate today = LocalDate.now(clock);
        for (FxRequest r : body) {
            if ("USD".equals(r.currency())) throw ApiException.badRequest("VALIDATION_FAILED", "USD siempre vale 1");
            fx.upsert(r.currency(), r.rateToUsd(), today);
        }
        return fxRates();
    }
}
