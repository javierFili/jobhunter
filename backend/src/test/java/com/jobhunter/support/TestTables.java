package com.jobhunter.support;

import java.util.List;

/** Statements run before each integration test: global (non user-owned) state is reset to the seeded defaults. */
final class TestTables {

    static final List<String> EXTRA_CLEANUP = List.of(
            "DELETE FROM applications",
            "DELETE FROM cover_letters",
            "DELETE FROM job_offers",
            "DELETE FROM job_source_executions",
            "DELETE FROM api_usage_logs",
            "DELETE FROM job_sources WHERE code LIKE 'apify:%'",
            "DELETE FROM apify_actors",
            "DELETE FROM fx_rates",
            "DELETE FROM ai_analyses",
            "UPDATE ai_task_routes SET provider = 'DEEPSEEK', model = IF(prompt_key = 'CV_OPTIMIZE', 'deepseek-v4-pro', 'deepseek-flash'), fallback_provider = NULL, fallback_model = NULL",
            "UPDATE job_sources SET last_fetch_at = NULL, enabled = TRUE",
            "UPDATE integration_settings SET secret_ciphertext = NULL, secret_last4 = NULL, status = 'UNKNOWN', "
                    + "last_error = NULL, last_error_at = NULL, last_success_at = NULL, "
                    + "enabled = (integration_code IN ('REMOTIVE','HIMALAYAS','JOBICY','REMOTEOK','GREENHOUSE','LEVER','ASHBY'))",
            // limits/budgets back to the V7/V8 seed values
            "UPDATE integration_settings SET monthly_budget_usd = CASE integration_code WHEN 'APIFY' THEN 5.00 "
                    + "WHEN 'DEEPSEEK' THEN 10.00 WHEN 'CLAUDE' THEN 10.00 ELSE NULL END, "
                    + "daily_limit = CASE integration_code WHEN 'ADZUNA' THEN 50 WHEN 'SERPAPI' THEN 50 WHEN 'REMOTIVE' THEN 4 "
                    + "WHEN 'HIMALAYAS' THEN 500 WHEN 'JOBICY' THEN 50 WHEN 'REMOTEOK' THEN 4 WHEN 'GREENHOUSE' THEN 500 "
                    + "WHEN 'LEVER' THEN 500 WHEN 'ASHBY' THEN 500 ELSE NULL END, "
                    + "monthly_limit = CASE integration_code WHEN 'ADZUNA' THEN 1000 WHEN 'SERPAPI' THEN 250 ELSE NULL END");

    private TestTables() {
    }
}
