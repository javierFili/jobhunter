package com.jobhunter.jobs.api;

import com.jobhunter.jobs.application.JobQueryService;
import com.jobhunter.jobs.application.MatchLookup;
import com.jobhunter.jobs.domain.JobOffer;
import com.jobhunter.jobs.domain.JobSource;
import com.jobhunter.jobs.domain.JobSourceReference;
import com.jobhunter.jobs.domain.UserJobState;
import com.jobhunter.profiles.application.SkillService;
import com.jobhunter.profiles.domain.Skill;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Builds list/detail DTOs with batched lookups (skills, sources, per-user state, match). */
@Component
public class JobViewAssembler {

    private final JobQueryService jobs;
    private final SkillService skills;
    private final ObjectProvider<MatchLookup> matches;

    public JobViewAssembler(JobQueryService jobs, SkillService skills, ObjectProvider<MatchLookup> matches) {
        this.jobs = jobs;
        this.skills = skills;
        this.matches = matches;
    }

    public List<JobDtos.JobSummaryDto> summaries(long userId, List<JobOffer> offers) {
        List<Long> ids = offers.stream().map(JobOffer::getId).toList();
        Map<Long, Skill> catalogue = skills.byIds(offers.stream().flatMap(o -> o.getSkillIds().stream()).toList());
        Map<Long, List<JobSourceReference>> refs = jobs.references(ids);
        Map<Long, JobSource> sources = jobs.sourcesById();
        Map<Long, UserJobState> states = jobs.states(userId, ids);
        Map<Long, MatchLookup.MatchSummary> m = matchMap(userId, ids);
        return offers.stream().map(o -> JobDtos.summary(o, catalogue, refs.get(o.getId()), sources, states.get(o.getId()),
                toRef(m.get(o.getId())))).toList();
    }

    public JobDtos.JobDetailDto detail(long userId, JobOffer o) {
        List<Long> ids = List.of(o.getId());
        return JobDtos.detail(o, skills.byIds(o.getSkillIds()), jobs.references(ids).getOrDefault(o.getId(), List.of()),
                jobs.sourcesById(), jobs.states(userId, ids).get(o.getId()), toRef(matchMap(userId, ids).get(o.getId())));
    }

    private Map<Long, MatchLookup.MatchSummary> matchMap(long userId, Collection<Long> ids) {
        MatchLookup lookup = matches.getIfAvailable();
        return lookup == null || ids.isEmpty() ? Map.of() : lookup.forOffers(userId, ids);
    }

    private static JobDtos.MatchRef toRef(MatchLookup.MatchSummary s) {
        return s == null ? null : new JobDtos.MatchRef(s.scoreTotal(), s.classification());
    }
}
