package com.jobhunter.searches.domain;

import java.util.List;

/** Published after a search run; the matching module scores the found jobs (deterministic, no cost). */
public record SearchCompletedEvent(long userId, Long profileId, List<Long> offerIds) {
}
