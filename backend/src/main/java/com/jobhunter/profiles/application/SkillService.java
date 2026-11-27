package com.jobhunter.profiles.application;

import com.jobhunter.profiles.domain.Enums.EvidenceLevel;
import com.jobhunter.profiles.domain.Enums.SkillCategory;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.ExperienceYears;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.profiles.domain.UserSkill;
import com.jobhunter.profiles.infrastructure.ExperienceRepository;
import com.jobhunter.profiles.infrastructure.SkillRepository;
import com.jobhunter.profiles.infrastructure.UserSkillRepository;
import com.jobhunter.shared.api.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final ExperienceRepository experiences;
    private final Clock clock;
    private final SkillDictionary dictionary;

    public SkillService(SkillRepository skills, UserSkillRepository userSkills, ExperienceRepository experiences,
                        Clock clock, SkillDictionary dictionary) {
        this.skills = skills;
        this.userSkills = userSkills;
        this.experiences = experiences;
        this.clock = clock;
        this.dictionary = dictionary;
    }

    public record UserSkillView(Long skillId, String name, String category, String evidenceLevel, Integer selfRating,
                                Integer lastUsedYear, BigDecimal yearsProfessional, boolean verified) {
    }

    @Transactional(readOnly = true)
    public List<Skill> search(String q, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        String norm = Skill.normalize(q == null ? "" : q);
        if (norm.isEmpty()) {
            return skills.findAll(PageRequest.of(0, size)).getContent();
        }
        Map<Long, Skill> result = new LinkedHashMap<>();
        skills.searchByPrefix(norm, PageRequest.of(0, size)).forEach(s -> result.put(s.getId(), s));
        if (result.size() < size) {
            skills.searchContaining(norm, PageRequest.of(0, size)).forEach(s -> result.putIfAbsent(s.getId(), s));
        }
        return result.values().stream().limit(size).toList();
    }

    /** Creates a catalogue entry, or returns the existing one with the same normalized name. */
    @Transactional
    public Skill createOrGet(String name, SkillCategory category) {
        String norm = Skill.normalize(name);
        if (norm == null || norm.isBlank() || norm.length() > 100) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Nombre de habilidad inválido");
        }
        return skills.findByNormalizedName(norm).orElseGet(() -> {
            Skill created = skills.save(new Skill(name, category));
            dictionary.invalidate();
            return created;
        });
    }

    @Transactional(readOnly = true)
    public Map<Long, Skill> byIds(Collection<Long> ids) {
        return skills.findAllById(ids).stream().collect(Collectors.toMap(Skill::getId, s -> s));
    }

    /** Throws if any id is not in the catalogue. */
    @Transactional(readOnly = true)
    public void requireExisting(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Set<Long> unique = Set.copyOf(ids);
        if (skills.findAllById(unique).size() != unique.size()) {
            throw ApiException.badRequest("UNKNOWN_SKILL", "Una o más habilidades no existen en el catálogo");
        }
    }

    @Transactional(readOnly = true)
    public List<UserSkillView> list(long userId) {
        List<UserSkill> mine = userSkills.findByUserId(userId);
        Map<Long, Skill> catalogue = byIds(mine.stream().map(UserSkill::getSkillId).toList());
        Map<Long, BigDecimal> years = professionalYears(userId);
        return mine.stream()
                .map(us -> view(us, catalogue.get(us.getSkillId()), years.getOrDefault(us.getSkillId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(UserSkillView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public UserSkillView upsert(long userId, long skillId, EvidenceLevel level, Integer selfRating, Integer lastUsedYear) {
        Skill skill = skills.findById(skillId).orElseThrow(() -> ApiException.notFound("Habilidad"));
        if (selfRating != null && (selfRating < 1 || selfRating > 5)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "La autoevaluación debe estar entre 1 y 5");
        }
        UserSkill us = userSkills.findByUserIdAndSkillId(userId, skillId)
                .orElseGet(() -> new UserSkill(userId, skillId, true));
        us.update(level, selfRating, lastUsedYear);
        userSkills.save(us);
        return view(us, skill, professionalYears(userId).getOrDefault(skillId, BigDecimal.ZERO));
    }

    @Transactional
    public void delete(long userId, long skillId) {
        userSkills.findByUserIdAndSkillId(userId, skillId).ifPresent(userSkills::delete);
    }

    /**
     * Ensures skills referenced by an experience/project exist in the user's skill list. Never downgrades an
     * existing evidence level; upgrades PROJECT_ONLY/ACADEMIC/LEARNING to PROFESSIONAL when used in employment.
     */
    @Transactional
    public void ensureUserSkills(long userId, Collection<Long> skillIds, EvidenceLevel level, boolean verified) {
        if (skillIds == null || skillIds.isEmpty()) return;
        Map<Long, UserSkill> existing = new HashMap<>();
        userSkills.findByUserIdAndSkillIdIn(userId, skillIds).forEach(us -> existing.put(us.getSkillId(), us));
        for (Long skillId : Set.copyOf(skillIds)) {
            UserSkill us = existing.get(skillId);
            if (us == null) {
                us = new UserSkill(userId, skillId, verified);
                us.update(level, null, null);
                userSkills.save(us);
            } else if (level == EvidenceLevel.PROFESSIONAL && us.getEvidenceLevel() != EvidenceLevel.PROFESSIONAL) {
                us.update(EvidenceLevel.PROFESSIONAL, us.getSelfRating(), us.getLastUsedYear());
            }
        }
    }

    /** Years of professional use per skill, computed from the dates of experiences that list the skill. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> professionalYears(long userId) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<ExperienceYears.Period>> periods = new HashMap<>();
        for (Experience e : experiences.findByUserId(userId)) {
            for (Long skillId : e.getSkillIds()) {
                periods.computeIfAbsent(skillId, k -> new java.util.ArrayList<>())
                        .add(new ExperienceYears.Period(e.getStartDate(), e.getEndDate()));
            }
        }
        Map<Long, BigDecimal> years = new HashMap<>();
        periods.forEach((skillId, list) -> years.put(skillId, ExperienceYears.of(list, today)));
        return years;
    }

    private static UserSkillView view(UserSkill us, Skill skill, BigDecimal years) {
        return new UserSkillView(us.getSkillId(), skill == null ? "?" : skill.getName(),
                skill == null ? null : skill.getCategory().name(),
                us.getEvidenceLevel() == null ? null : us.getEvidenceLevel().name(), us.getSelfRating(),
                us.getLastUsedYear(), years, us.isVerified());
    }
}
