package com.jobhunter.jobs.application;

import java.util.Collection;
import java.util.Map;

/** Implemented by the matching module (Phase 4): the user's latest match per offer, for list/detail views. */
public interface MatchLookup {

    record MatchSummary(int scoreTotal, String classification) {
    }

    Map<Long, MatchSummary> forOffers(long userId, Collection<Long> offerIds);
}
