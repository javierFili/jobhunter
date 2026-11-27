package com.jobhunter.analytics.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only aggregate queries for the dashboard and analytics page (a read model over several modules' tables).
 * Everything here is OBSERVED data: counts over jobs the app collected and the user's own applications.
 */
@Service
public class AnalyticsService {

    private static final Set<String> DIMENSIONS = Set.of("country", "region", "source", "technology", "modality",
            "salaryBand", "engagement", "eligibility");

    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Period(Instant from, Instant to) {
        public static Period of(LocalDate from, LocalDate to) {
            LocalDate f = from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(29);
            LocalDate t = to != null ? to : LocalDate.now(ZoneOffset.UTC);
            if (t.isBefore(f)) t = f;
            return new Period(f.atStartOfDay().toInstant(ZoneOffset.UTC), t.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        }

        Timestamp fromTs() { return Timestamp.from(from); }
        Timestamp toTs() { return Timestamp.from(to); }
    }

    public record Overview(Map<String, Long> jobs, Map<String, Object> applications, Map<String, Long> matches,
                           Map<String, Object> usage) {
    }

    @Transactional(readOnly = true)
    public Overview overview(long userId, Period p) {
        Map<String, Long> jobs = new LinkedHashMap<>();
        jobs.put("total", count("SELECT COUNT(*) FROM job_offers"));
        jobs.put("active", count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE'"));
        jobs.put("newInPeriod", count("SELECT COUNT(*) FROM job_offers WHERE first_seen_at >= ? AND first_seen_at < ?", p.fromTs(), p.toTs()));
        jobs.put("remote", count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE' AND work_modality = 'REMOTE'"));
        jobs.put("freelance", count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE' AND (engagement_type IN ('FREELANCE_PROJECT','CONTRACTOR') OR contract_type = 'FREELANCE')"));
        jobs.put("eligibleOrLikely", count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE' AND bolivia_eligibility IN ('ELIGIBLE','LIKELY')"));

        String base = "FROM applications WHERE user_id = ? AND deleted_at IS NULL";
        long total = count("SELECT COUNT(*) " + base, userId);
        long applied = count("SELECT COUNT(*) " + base + " AND applied_at IS NOT NULL", userId);
        long responses = count("SELECT COUNT(*) " + base + " AND applied_at IS NOT NULL AND status IN ('RESPONSE_RECEIVED','INTERVIEW','OFFER','REJECTED')", userId);
        long interviews = reachedEver(userId, "INTERVIEW");
        long offers = reachedEver(userId, "OFFER");
        long rejections = count("SELECT COUNT(*) " + base + " AND status = 'REJECTED'", userId);
        Map<String, Object> apps = new LinkedHashMap<>();
        apps.put("total", total);
        apps.put("applied", applied);
        apps.put("responses", responses);
        apps.put("interviews", interviews);
        apps.put("offers", offers);
        apps.put("rejections", rejections);
        apps.put("responseRate", applied == 0 ? null : Math.round(1000.0 * responses / applied) / 1000.0);

        Map<String, Long> matches = new LinkedHashMap<>();
        for (String c : List.of("HIGH_MATCH", "MEDIUM_MATCH", "LOW_MATCH", "NOT_ELIGIBLE", "NEEDS_REVIEW")) matches.put(c, 0L);
        jdbc.query("SELECT classification, COUNT(*) FROM job_matches WHERE user_id = ? GROUP BY classification",
                rs -> { matches.put(rs.getString(1), rs.getLong(2)); }, userId);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("aiCostUsd", sum("SELECT COALESCE(SUM(cost_usd),0) FROM api_usage_logs WHERE integration_code IN ('DEEPSEEK','CLAUDE') AND occurred_at >= ? AND occurred_at < ?", p));
        usage.put("apifyCostUsd", sum("SELECT COALESCE(SUM(cost_usd),0) FROM api_usage_logs WHERE integration_code = 'APIFY' AND occurred_at >= ? AND occurred_at < ?", p));
        usage.put("totalRequests", count("SELECT COALESCE(SUM(request_count),0) FROM api_usage_logs WHERE occurred_at >= ? AND occurred_at < ?", p.fromTs(), p.toTs()));
        return new Overview(jobs, apps, matches, usage);
    }

    private long reachedEver(long userId, String status) {
        return count("""
                SELECT COUNT(DISTINCT a.id) FROM applications a
                LEFT JOIN application_events e ON e.application_id = a.id AND e.to_status = ?
                WHERE a.user_id = ? AND a.deleted_at IS NULL AND (a.status = ? OR e.id IS NOT NULL)""", status, userId, status);
    }

    public record Bucket(String key, String label, long count) {
    }

    @Transactional(readOnly = true)
    public List<Bucket> breakdown(String dimension, Period p, int limit) {
        if (!DIMENSIONS.contains(dimension)) throw new IllegalArgumentException("dimension");
        int lim = Math.max(1, Math.min(50, limit));
        String where = " WHERE o.first_seen_at >= ? AND o.first_seen_at < ? ";
        String sql = switch (dimension) {
            case "country" -> "SELECT COALESCE(o.country,'??') k, COALESCE(o.country,'Sin país') l, COUNT(*) c FROM job_offers o" + where + "GROUP BY k, l";
            case "region" -> "SELECT COALESCE(o.region, o.remote_scope) k, COALESCE(o.region, o.remote_scope) l, COUNT(*) c FROM job_offers o" + where + "GROUP BY k, l";
            case "modality" -> "SELECT o.work_modality k, o.work_modality l, COUNT(*) c FROM job_offers o" + where + "GROUP BY k, l";
            case "engagement" -> "SELECT o.engagement_type k, o.engagement_type l, COUNT(*) c FROM job_offers o" + where + "GROUP BY k, l";
            case "eligibility" -> "SELECT o.bolivia_eligibility k, o.bolivia_eligibility l, COUNT(*) c FROM job_offers o" + where + "GROUP BY k, l";
            case "source" -> "SELECT s.code k, s.display_name l, COUNT(DISTINCT o.id) c FROM job_offers o "
                    + "JOIN job_source_references r ON r.job_offer_id = o.id JOIN job_sources s ON s.id = r.job_source_id" + where + "GROUP BY k, l";
            case "technology" -> "SELECT CAST(sk.id AS CHAR) k, sk.name l, COUNT(*) c FROM job_offers o "
                    + "JOIN job_offer_skills jos ON jos.job_offer_id = o.id JOIN skills sk ON sk.id = jos.skill_id" + where + "GROUP BY k, l";
            default -> "SELECT b k, b l, COUNT(*) c FROM (SELECT CASE WHEN o.salary_min_usd_month IS NULL THEN 'SIN_DATO' "
                    + "WHEN o.salary_min_usd_month < 1000 THEN 'MENOS_1000' WHEN o.salary_min_usd_month < 2000 THEN '1000_2000' "
                    + "WHEN o.salary_min_usd_month < 3000 THEN '2000_3000' WHEN o.salary_min_usd_month < 5000 THEN '3000_5000' "
                    + "ELSE 'MAS_5000' END b FROM job_offers o" + where + ") x GROUP BY k, l";
        };
        return jdbc.query(sql + " ORDER BY c DESC LIMIT " + lim, (rs, i) -> new Bucket(rs.getString("k"), rs.getString("l"), rs.getLong("c")),
                p.fromTs(), p.toTs());
    }

    public record DayPoint(LocalDate date, long newJobs, long eligibleOrLikely) {
    }

    @Transactional(readOnly = true)
    public List<DayPoint> timeline(Period p) {
        return jdbc.query("""
                SELECT DATE(first_seen_at) d, COUNT(*) n, SUM(bolivia_eligibility IN ('ELIGIBLE','LIKELY')) e
                FROM job_offers WHERE first_seen_at >= ? AND first_seen_at < ? GROUP BY d ORDER BY d""",
                (rs, i) -> new DayPoint(rs.getDate("d").toLocalDate(), rs.getLong("n"), rs.getLong("e")), p.fromTs(), p.toTs());
    }

    public record Stage(String stage, long count) {
    }

    @Transactional(readOnly = true)
    public List<Stage> funnel(long userId, Period p) {
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("CREATED", count("SELECT COUNT(*) FROM applications WHERE user_id = ? AND deleted_at IS NULL "
                + "AND created_at >= ? AND created_at < ?", userId, p.fromTs(), p.toTs())));
        for (String s : List.of("APPLIED", "RESPONSE_RECEIVED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN")) {
            stages.add(new Stage(s, count("""
                    SELECT COUNT(DISTINCT e.application_id) FROM application_events e JOIN applications a ON a.id = e.application_id
                    WHERE a.user_id = ? AND a.deleted_at IS NULL AND e.to_status = ? AND e.occurred_at >= ? AND e.occurred_at < ?""",
                    userId, s, p.fromTs(), p.toTs())));
        }
        return stages;
    }

    public record SkillDemand(long skillId, String name, long jobs, double share, String userEvidence) {
    }

    /** Observed demand: share of ACTIVE jobs first seen in the last {@code days} days that mention each technology. */
    @Transactional(readOnly = true)
    public List<SkillDemand> skillDemand(long userId, String region, int days, int limit) {
        Timestamp since = Timestamp.from(Instant.now().minusSeconds(86400L * Math.max(1, Math.min(365, days))));
        String regionSql = region == null || region.isBlank() ? "" : " AND (o.region = ? OR o.remote_scope = ? OR o.remote_scope = 'WORLDWIDE')";
        List<Object> args = new ArrayList<>(List.of(since));
        if (!regionSql.isEmpty()) {
            args.add(region);
            args.add(region);
        }
        long total = count("SELECT COUNT(*) FROM job_offers o WHERE o.status = 'ACTIVE' AND o.first_seen_at >= ?" + regionSql, args.toArray());
        if (total == 0) return List.of();
        List<Object> args2 = new ArrayList<>();
        args2.add(userId);
        args2.addAll(args);
        String sql = "SELECT sk.id, sk.name, COUNT(*) c, us.evidence_level ev FROM job_offers o "
                + "JOIN job_offer_skills jos ON jos.job_offer_id = o.id JOIN skills sk ON sk.id = jos.skill_id "
                + "LEFT JOIN user_skills us ON us.skill_id = sk.id AND us.user_id = ? "
                + "WHERE o.status = 'ACTIVE' AND o.first_seen_at >= ?" + regionSql
                + " GROUP BY sk.id, sk.name, us.evidence_level ORDER BY c DESC LIMIT " + Math.max(1, Math.min(100, limit));
        return jdbc.query(sql,
                (rs, i) -> new SkillDemand(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        Math.round(10000.0 * rs.getLong(3) / total) / 10000.0, rs.getString(4)), args2.toArray());
    }

    public long jobsObserved(String region, int days) {
        Timestamp since = Timestamp.from(Instant.now().minusSeconds(86400L * Math.max(1, Math.min(365, days))));
        if (region == null || region.isBlank()) {
            return count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE' AND first_seen_at >= ?", since);
        }
        return count("SELECT COUNT(*) FROM job_offers WHERE status = 'ACTIVE' AND first_seen_at >= ? AND (region = ? OR remote_scope = ? OR remote_scope = 'WORLDWIDE')",
                since, region, region);
    }

    public record UsageDay(LocalDate date, String code, long requests, BigDecimal costUsd) {
    }

    @Transactional(readOnly = true)
    public List<UsageDay> usageDaily(Period p) {
        return jdbc.query("""
                SELECT DATE(occurred_at) d, integration_code c, SUM(request_count) r, SUM(cost_usd) cost FROM api_usage_logs
                WHERE occurred_at >= ? AND occurred_at < ? GROUP BY d, c ORDER BY d, c""",
                (rs, i) -> new UsageDay(rs.getDate("d").toLocalDate(), rs.getString("c"), rs.getLong("r"), rs.getBigDecimal("cost")),
                p.fromTs(), p.toTs());
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private BigDecimal sum(String sql, Period p) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, p.fromTs(), p.toTs());
        return v == null ? BigDecimal.ZERO : v;
    }
}
