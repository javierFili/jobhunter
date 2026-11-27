-- Phase 7: indexes matching the real query patterns (found with EXPLAIN): the catalog's default order is first_seen_at,
-- and recommended jobs are ranked per (user, profile).
CREATE INDEX ix_jo_status_first_seen ON job_offers (status, first_seen_at);
CREATE INDEX ix_jo_elig_status_first_seen ON job_offers (bolivia_eligibility, status, first_seen_at);
CREATE INDEX ix_match_profile_rank ON job_matches (user_id, profile_key, classification, score_total);
