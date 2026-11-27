package com.jobhunter.integrations.domain;

import java.util.List;

/**
 * Every external integration that can be switched on/off and (when needed) given credentials from the panel.
 * {@code credentialFields} empty = keyless public API (only the on/off switch applies).
 */
public enum IntegrationCode {

    DEEPSEEK("DeepSeek", Category.AI, List.of(new Field("apiKey", "API key", true, "JOBHUNTER_DEEPSEEK_API_KEY"))),
    CLAUDE("Claude (Anthropic)", Category.AI, List.of(new Field("apiKey", "API key", true, "JOBHUNTER_ANTHROPIC_API_KEY"))),
    APIFY("Apify", Category.SCRAPING, List.of(new Field("token", "API token", true, "JOBHUNTER_APIFY_TOKEN"))),
    ADZUNA("Adzuna", Category.JOB_SOURCE, List.of(new Field("appId", "App ID", true, "JOBHUNTER_ADZUNA_APP_ID"),
            new Field("appKey", "App key", true, "JOBHUNTER_ADZUNA_APP_KEY"))),
    SERPAPI("SerpApi (Google Jobs)", Category.JOB_SOURCE, List.of(new Field("apiKey", "API key", true, "JOBHUNTER_SERPAPI_KEY"))),
    REMOTIVE("Remotive", Category.JOB_SOURCE, List.of()),
    HIMALAYAS("Himalayas", Category.JOB_SOURCE, List.of(new Field("apiKey", "API key (opcional)", false, "JOBHUNTER_HIMALAYAS_API_KEY"))),
    JOBICY("Jobicy", Category.JOB_SOURCE, List.of()),
    REMOTEOK("Remote OK", Category.JOB_SOURCE, List.of()),
    GREENHOUSE("Greenhouse", Category.JOB_SOURCE, List.of()),
    LEVER("Lever", Category.JOB_SOURCE, List.of()),
    ASHBY("Ashby", Category.JOB_SOURCE, List.of()),
    GETONBOARD("Get on Board", Category.JOB_SOURCE, List.of()),
    WEWORKREMOTELY("We Work Remotely", Category.JOB_SOURCE, List.of()),
    LARAJOBS("LaraJobs", Category.JOB_SOURCE, List.of()),
    FREELANCER("Freelancer.com", Category.JOB_SOURCE, List.of());

    public enum Category { AI, JOB_SOURCE, SCRAPING }

    public record Field(String name, String label, boolean required, String envVar) {
    }

    private final String displayName;
    private final Category category;
    private final List<Field> credentialFields;

    IntegrationCode(String displayName, Category category, List<Field> credentialFields) {
        this.displayName = displayName;
        this.category = category;
        this.credentialFields = credentialFields;
    }

    public String displayName() { return displayName; }
    public Category category() { return category; }
    public List<Field> credentialFields() { return credentialFields; }

    public boolean requiresCredentials() {
        return credentialFields.stream().anyMatch(Field::required);
    }
}
