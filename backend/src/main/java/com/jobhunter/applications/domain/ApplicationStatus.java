package com.jobhunter.applications.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Application pipeline. APPLIED is never reached through a plain transition: only via "mark as submitted", which
 * records that the USER sent the application (JobHunter never sends anything).
 */
public enum ApplicationStatus {
    DISCOVERED, ANALYZED, FAVORITE, CV_OPTIMIZED, READY_TO_APPLY, APPLIED, RESPONSE_RECEIVED, INTERVIEW, OFFER, REJECTED,
    WITHDRAWN;

    public static final Set<ApplicationStatus> PRE_APPLIED = EnumSet.of(DISCOVERED, ANALYZED, FAVORITE, CV_OPTIMIZED, READY_TO_APPLY);

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(DISCOVERED, EnumSet.of(ANALYZED, FAVORITE, CV_OPTIMIZED, READY_TO_APPLY, WITHDRAWN)),
            Map.entry(ANALYZED, EnumSet.of(DISCOVERED, FAVORITE, CV_OPTIMIZED, READY_TO_APPLY, WITHDRAWN)),
            Map.entry(FAVORITE, EnumSet.of(DISCOVERED, ANALYZED, CV_OPTIMIZED, READY_TO_APPLY, WITHDRAWN)),
            Map.entry(CV_OPTIMIZED, EnumSet.of(FAVORITE, READY_TO_APPLY, WITHDRAWN)),
            Map.entry(READY_TO_APPLY, EnumSet.of(CV_OPTIMIZED, FAVORITE, WITHDRAWN)),
            Map.entry(APPLIED, EnumSet.of(RESPONSE_RECEIVED, INTERVIEW, OFFER, REJECTED, WITHDRAWN)),
            Map.entry(RESPONSE_RECEIVED, EnumSet.of(INTERVIEW, OFFER, REJECTED, WITHDRAWN)),
            Map.entry(INTERVIEW, EnumSet.of(RESPONSE_RECEIVED, OFFER, REJECTED, WITHDRAWN)),
            Map.entry(OFFER, EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN)),
            Map.entry(REJECTED, EnumSet.noneOf(ApplicationStatus.class)),
            Map.entry(WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class)));

    public Set<ApplicationStatus> allowedTargets() {
        return TRANSITIONS.get(this);
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public static Map<ApplicationStatus, Set<ApplicationStatus>> transitions() {
        return TRANSITIONS;
    }
}
