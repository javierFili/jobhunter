package com.jobhunter.jobs.application;

import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.domain.JobSourceReference;
import com.jobhunter.jobs.domain.UserJobState;
import com.jobhunter.jobs.infrastructure.JobOfferRepository;
import com.jobhunter.jobs.infrastructure.JobSourceReferenceRepository;
import com.jobhunter.jobs.infrastructure.JobSourceRepository;
import com.jobhunter.jobs.infrastructure.UserJobStateRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of the job catalogue: filtered, paginated queries and per-user state (saved/hidden). */
@Service
public class JobQueryService {

    private final JobOfferRepository offers;
    private final JobSourceReferenceRepository references;
    private final JobSourceRepository sources;
    private final UserJobStateRepository states;
    private final Clock clock;

    public JobQueryService(JobOfferRepository offers, JobSourceReferenceRepository references,
                           JobSourceRepository sources, UserJobStateRepository states, Clock clock) {
        this.offers = offers;
        this.references = references;
        this.sources = sources;
        this.states = states;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<JobOffer> search(long userId, JobQuery q, Collection<Long> restrictToIds, int page, int size) {
        if (restrictToIds != null && restrictToIds.isEmpty()) {
            return Page.empty(PageRequest.of(Math.max(0, page), Math.max(1, size)));
        }
        Sort sort = switch (q.sort() == null ? "recent" : q.sort()) {
            case "published" -> Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("id"));
            case "salary" -> Sort.by(Sort.Order.desc("salaryMinUsdMonth").nullsLast(), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("firstSeenAt"), Sort.Order.desc("id"));
        };
        return offers.findAll(spec(userId, q, restrictToIds), PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort));
    }

    /** All ids matching the query within a restricted set (used to apply saved-search filters to a run's results). */
    @Transactional(readOnly = true)
    public List<Long> matchingIds(long userId, JobQuery q, Collection<Long> restrictToIds) {
        if (restrictToIds.isEmpty()) return List.of();
        return offers.findAll(spec(userId, q, restrictToIds)).stream().map(JobOffer::getId).toList();
    }

    Specification<JobOffer> spec(long userId, JobQuery q, Collection<Long> restrictToIds) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (restrictToIds != null) p.add(root.get("id").in(restrictToIds));
            if (q.status() != null && !q.status().isBlank() && !"ANY".equals(q.status())) p.add(cb.equal(root.get("status"), q.status()));
            if (q.q() != null && !q.q().isBlank()) p.add(keyword(root, cb, q.q()));
            if (JobQuery.has(q.skillIds())) {
                for (Long skillId : q.skillIds()) p.add(cb.isMember(skillId, root.<Collection<Long>>get("skillIds")));
            }
            if (JobQuery.has(q.countries())) {
                List<Predicate> any = new ArrayList<>();
                any.add(root.get("country").in(q.countries()));
                any.add(cb.and(cb.equal(root.get("workModality"), "REMOTE"), root.get("remoteScope").in(q.scopesCoveringCountries())));
                any.add(cb.greaterThan(cb.function("json_overlaps", Integer.class, root.get("remoteCountries"),
                        cb.literal(Json.write(q.countries()))), 0));
                p.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (JobQuery.has(q.regions())) {
                p.add(cb.or(root.get("region").in(q.regions()), root.get("remoteScope").in(q.regions())));
            }
            inIfPresent(p, root, "workModality", q.modalities());
            inIfPresent(p, root, "remoteScope", q.remoteScopes());
            inIfPresent(p, root, "boliviaEligibility", q.eligibility());
            inIfPresent(p, root, "contractType", q.contractTypes());
            inIfPresent(p, root, "engagementType", q.engagementTypes());
            inIfPresent(p, root, "seniority", q.seniority());
            if (q.salaryMinUsdMonth() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("salaryMinUsdMonth"), q.salaryMinUsdMonth()));
            }
            if (JobQuery.has(q.languages())) {
                // MySQL stores JSON canonically as {"code": "en", ...} (space after the colon); accept both forms
                List<Predicate> any = new ArrayList<>();
                for (String code : q.languages()) {
                    String c = code.replaceAll("[^a-z]", "");
                    any.add(cb.like(root.get("requiredLanguages"), "%\"code\": \"" + c + "\"%"));
                    any.add(cb.like(root.get("requiredLanguages"), "%\"code\":\"" + c + "\"%"));
                }
                p.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (q.postedWithinDays() != null) {
                Instant since = clock.instant().minus(Duration.ofDays(q.postedWithinDays()));
                p.add(cb.greaterThanOrEqualTo(cb.coalesce(root.<Instant>get("publishedAt"), root.<Instant>get("firstSeenAt")), since));
            }
            if (JobQuery.has(q.companies())) {
                p.add(cb.or(q.companies().stream().map(c -> cb.like(root.get("normalizedCompany"),
                        "%" + escapeLike(c.toLowerCase(Locale.ROOT)) + "%", '\\')).toArray(Predicate[]::new)));
            }
            if (JobQuery.has(q.excludeCompanies())) {
                for (String c : q.excludeCompanies()) {
                    p.add(cb.or(cb.isNull(root.get("normalizedCompany")),
                            cb.notLike(root.get("normalizedCompany"), "%" + escapeLike(c.toLowerCase(Locale.ROOT)) + "%", '\\')));
                }
            }
            if (JobQuery.has(q.sources())) {
                List<Long> sourceIds = q.sources().stream().map(code -> sources.findByCode(code).map(JobSource::getId).orElse(-1L)).toList();
                Subquery<Long> sq = query.subquery(Long.class);
                Root<JobSourceReference> r = sq.from(JobSourceReference.class);
                sq.select(r.get("jobOfferId")).where(r.get("jobSourceId").in(sourceIds));
                p.add(root.get("id").in(sq));
            }
            if (Boolean.TRUE.equals(q.saved()) || !q.includeHidden()) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<UserJobState> s = sq.from(UserJobState.class);
                if (Boolean.TRUE.equals(q.saved())) {
                    sq.select(s.get("id").get("jobOfferId")).where(cb.equal(s.get("id").get("userId"), userId), cb.isTrue(s.get("saved")));
                    p.add(root.get("id").in(sq));
                } else {
                    sq.select(s.get("id").get("jobOfferId")).where(cb.equal(s.get("id").get("userId"), userId), cb.isTrue(s.get("hidden")));
                    p.add(cb.not(root.get("id").in(sq)));
                }
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** FULLTEXT (boolean mode) for words ≥ 3 chars; shorter tokens (e.g. "Go", "C#") fall back to LIKE on the title. */
    private static Predicate keyword(Root<JobOffer> root, CriteriaBuilder cb, String raw) {
        List<String> longWords = new ArrayList<>();
        List<Predicate> shortWords = new ArrayList<>();
        for (String w : raw.trim().split("\\s+")) {
            String clean = w.replaceAll("[+\\-<>()~*\"@]", "").trim();
            if (clean.isEmpty()) continue;
            if (clean.length() >= 3 && clean.matches("[\\p{L}\\p{N}]+")) longWords.add("+" + clean + "*");
            else shortWords.add(cb.like(cb.lower(root.get("title")), "%" + escapeLike(clean.toLowerCase(Locale.ROOT)) + "%", '\\'));
        }
        List<Predicate> all = new ArrayList<>(shortWords);
        if (!longWords.isEmpty()) {
            Expression<Double> score = cb.function("match_against", Double.class, root.get("title"), root.get("descriptionText"),
                    cb.literal(String.join(" ", longWords)));
            all.add(cb.greaterThan(score, 0.0));
        }
        return all.isEmpty() ? cb.conjunction() : cb.and(all.toArray(Predicate[]::new));
    }

    private static void inIfPresent(List<Predicate> p, Root<JobOffer> root, String attr, List<String> values) {
        if (JobQuery.has(values)) p.add(root.get(attr).in(values));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---- details & state ----

    @Transactional(readOnly = true)
    public JobOffer get(long id) {
        return offers.findById(id).orElseThrow(() -> ApiException.notFound("Oferta"));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<JobSourceReference>> references(Collection<Long> offerIds) {
        if (offerIds.isEmpty()) return Map.of();
        return references.findByJobOfferIdIn(offerIds).stream().collect(Collectors.groupingBy(JobSourceReference::getJobOfferId));
    }

    @Transactional(readOnly = true)
    public Map<Long, JobSource> sourcesById() {
        return sources.findAll().stream().collect(Collectors.toMap(JobSource::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<Long, UserJobState> states(long userId, Collection<Long> offerIds) {
        if (offerIds.isEmpty()) return Map.of();
        return states.findForUser(userId, offerIds).stream().collect(Collectors.toMap(s -> s.getId().jobOfferId(), s -> s));
    }

    @Transactional
    public UserJobState updateState(long userId, long offerId, Boolean saved, Boolean hidden) {
        get(offerId);
        UserJobState state = states.findById(new UserJobState.Id(userId, offerId))
                .orElseGet(() -> new UserJobState(userId, offerId));
        state.update(saved, hidden);
        return states.save(state);
    }

    public record CatalogStats(long total, long newLast7Days, long eligibleOrLikely, long saved) {
    }

    @Transactional(readOnly = true)
    public CatalogStats stats(long userId) {
        return new CatalogStats(offers.countActive(), offers.countNewSince(clock.instant().minus(Duration.ofDays(7))),
                offers.countEligibleOrLikely(), states.countSaved(userId));
    }
}
