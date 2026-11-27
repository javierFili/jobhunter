package com.jobhunter.jobs.application;

import com.jobhunter.jobs.domain.EligibilityClassifier;
import com.jobhunter.jobs.domain.JobClassification;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobOfferDraft;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.domain.JobSourceReference;
import com.jobhunter.jobs.domain.JobText;
import com.jobhunter.jobs.domain.NormalizedJob;
import com.jobhunter.jobs.domain.SalaryParser;
import com.jobhunter.jobs.infrastructure.JobOfferRepository;
import com.jobhunter.jobs.infrastructure.JobSourceReferenceRepository;
import com.jobhunter.profiles.application.SkillDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns connector drafts into deduplicated {@link JobOffer}s:
 * sanitize → classify (deterministic) → tag technologies → dedupe (source id → canonical URL → fingerprint+SimHash)
 * → upsert offer + keep a separate {@link JobSourceReference} per source. Each draft is its own transaction so one
 * bad item never aborts a whole run.
 */
@Service
public class JobIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JobIngestionService.class);
    static final int NEAR_DUPLICATE_DISTANCE = 3;
    static final Duration DUPLICATE_WINDOW = Duration.ofDays(45);

    private final JobOfferRepository offers;
    private final JobSourceReferenceRepository references;
    private final SkillDictionary dictionary;
    private final FxService fx;
    private final TransactionTemplate tx;
    private final Clock clock;

    public JobIngestionService(JobOfferRepository offers, JobSourceReferenceRepository references,
                               SkillDictionary dictionary, FxService fx, PlatformTransactionManager txManager,
                               Clock clock) {
        this.offers = offers;
        this.references = references;
        this.dictionary = dictionary;
        this.fx = fx;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public enum Outcome { NEW, UPDATED, MERGED_DUPLICATE, UNCHANGED }

    public record Ingested(Long offerId, Outcome outcome, boolean materialChange) {
    }

    public record Result(List<Ingested> items, int failed) {
        public long count(Outcome o) {
            return items.stream().filter(i -> i.outcome() == o).count();
        }
    }

    public Result ingest(JobSource source, List<JobOfferDraft> drafts) {
        Map<String, BigDecimal> rates = fx.rates();
        List<Ingested> result = new ArrayList<>();
        int failed = 0;
        for (JobOfferDraft draft : drafts) {
            if (draft.title() == null || draft.title().isBlank() || draft.sourceUrl() == null) {
                failed++;
                continue;
            }
            try {
                Ingested i = tx.execute(status -> ingestOne(source, draft, rates));
                if (i != null) result.add(i);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Ingestion failed for {} {}: {}", source.getCode(), draft.externalId(), e.toString());
            }
        }
        return new Result(result, failed);
    }

    Ingested ingestOne(JobSource source, JobOfferDraft draft, Map<String, BigDecimal> rates) {
        Instant now = clock.instant();
        NormalizedJob n = normalize(draft, rates);
        String externalId = externalId(draft);
        var existingRef = references.findByJobSourceIdAndExternalId(source.getId(), externalId);
        JobOffer offer;
        Outcome outcome;
        if (existingRef.isPresent()) {
            offer = offers.findById(existingRef.get().getJobOfferId()).orElseThrow();
            outcome = Outcome.UPDATED;
        } else {
            offer = findDuplicate(n, now);
            outcome = offer == null ? Outcome.NEW : Outcome.MERGED_DUPLICATE;
            if (offer == null) offer = new JobOffer(now);
        }
        boolean changed = offer.apply(n);
        if (outcome == Outcome.UPDATED && !changed) outcome = Outcome.UNCHANGED;
        offer.seen(now);
        offer = offers.save(offer);

        JobSourceReference ref = existingRef.orElseGet(() -> null);
        if (ref == null) {
            ref = new JobSourceReference(offer.getId(), source.getId(), externalId, now);
        }
        ref.update(draft.sourceUrl(), draft.applyUrl(), draft.attribution() != null ? draft.attribution()
                : source.getAttributionText(), trimPayload(draft.rawPayload()), JobText.sha256(draft.rawPayload()), now);
        references.save(ref);
        return new Ingested(offer.getId(), outcome, changed);
    }

    /** canonical URL → fingerprint + SimHash near-duplicate (published within the window). */
    JobOffer findDuplicate(NormalizedJob n, Instant now) {
        if (n.canonicalUrl() != null) {
            List<JobOffer> byUrl = offers.findByCanonicalUrlHash(JobText.sha256(n.canonicalUrl()));
            if (!byUrl.isEmpty()) return byUrl.get(0);
        }
        for (JobOffer candidate : offers.findByFingerprint(n.fingerprint())) {
            Instant a = candidate.getPublishedAt() != null ? candidate.getPublishedAt() : candidate.getFirstSeenAt();
            Instant b = n.publishedAt() != null ? n.publishedAt() : now;
            boolean closeInTime = Duration.between(a, b).abs().compareTo(DUPLICATE_WINDOW) <= 0;
            boolean similar = candidate.getSimhash() == null || n.simhash() == 0
                    || JobText.hamming(candidate.getSimhash(), n.simhash()) <= NEAR_DUPLICATE_DISTANCE;
            if (closeInTime && similar) return candidate;
        }
        return null;
    }

    public NormalizedJob normalize(JobOfferDraft d, Map<String, BigDecimal> rates) {
        String html = JobText.sanitizeHtml(d.descriptionHtml());
        String text = d.descriptionText() != null && !d.descriptionText().isBlank()
                ? d.descriptionText().trim() : JobText.htmlToText(d.descriptionHtml());
        JobOfferDraft withText = new JobOfferDraft(d.sourceCode(), d.externalId(), d.sourceUrl(), d.applyUrl(),
                d.title(), d.companyName(), d.descriptionHtml(), text, d.locationText(), d.allowedLocations(),
                d.timezoneOffsets(), d.remote(), d.workplaceType(), d.employmentType(), d.seniority(), d.salaryMin(),
                d.salaryMax(), d.salaryCurrency(), d.salaryPeriod(), d.salaryEstimated(), d.salaryText(),
                d.publishedAt(), d.publishedApprox(), d.expiresAt(), d.countryHint(), d.attribution(), d.rawPayload());
        JobClassification c = EligibilityClassifier.classify(withText);

        BigDecimal min = d.salaryMin();
        BigDecimal max = d.salaryMax();
        String currency = d.salaryCurrency();
        String period = d.salaryPeriod();
        if (min == null && d.salaryText() != null) {
            var parsed = SalaryParser.parse(d.salaryText());
            if (parsed.isPresent()) {
                min = parsed.get().min();
                max = parsed.get().max();
                currency = parsed.get().currency();
                period = parsed.get().period();
            }
        }
        if (min != null && min.signum() <= 0) min = null;
        if (max != null && max.signum() <= 0) max = null;
        if (min == null && max != null) min = max;
        BigDecimal usdMonth = FxService.usdPerMonth(min, currency, period, rates);

        String title = d.title().trim();
        String normalizedTitle = JobText.normalizeTitle(title);
        String company = d.companyName() == null ? null : d.companyName().trim();
        String normalizedCompany = JobText.normalizeCompany(company);
        String canonical = JobText.canonicalUrl(d.sourceUrl());
        Set<Long> skillIds = new LinkedHashSet<>();
        dictionary.find(title + "\n" + (text == null ? "" : text)).forEach(m -> skillIds.add(m.skillId()));
        String locationKey = c.country() != null ? c.country() : c.remoteScope();
        byte[] contentHash = JobText.sha256(normalizedTitle + "|" + normalizedCompany + "|" + JobText.fold(text));
        return new NormalizedJob(truncate(title, 300), normalizedTitle, truncate(company, 200), normalizedCompany, html,
                text, truncate(d.locationText(), 500), c, min, max, currency, period, d.salaryEstimated(),
                truncate(d.salaryText(), 200), usdMonth, d.publishedAt(), d.publishedApprox(), d.expiresAt(),
                canonical != null && canonical.length() <= 1000 ? canonical : null, truncate(d.applyUrl(), 1000),
                contentHash, JobText.fingerprint(normalizedCompany, normalizedTitle, locationKey),
                JobText.simhash(text == null ? title : text), skillIds);
    }

    /**
     * For complete listings (ATS boards): references under {@code prefix} that were not returned anymore are marked
     * removed; an offer with no active reference left becomes REMOVED.
     */
    public int markMissing(JobSource source, String prefix, Set<String> seenExternalIds) {
        Integer removed = tx.execute(status -> {
            Instant now = clock.instant();
            int count = 0;
            for (JobSourceReference ref : references.findActiveBySourceAndPrefix(source.getId(), prefix)) {
                if (!seenExternalIds.contains(ref.getExternalId())) {
                    ref.markRemoved(now);
                    references.flush();
                    if (references.countActiveForOffer(ref.getJobOfferId()) == 0) {
                        offers.findById(ref.getJobOfferId()).ifPresent(JobOffer::remove);
                    }
                    count++;
                }
            }
            return count;
        });
        return removed == null ? 0 : removed;
    }

    public static String externalId(JobOfferDraft d) {
        String id = d.externalId() != null && !d.externalId().isBlank() ? d.externalId()
                : HexFormat.of().formatHex(JobText.sha256(d.sourceUrl()));
        return id.length() <= 200 ? id : HexFormat.of().formatHex(JobText.sha256(id));
    }

    private static String trimPayload(String raw) {
        // raw payload kept for re-normalization/debugging; skip very large ones
        return raw == null || raw.length() > 60_000 ? null : raw;
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
