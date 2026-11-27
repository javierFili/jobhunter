package com.jobhunter.profiles.application;

import com.jobhunter.profiles.application.Commands.ProfileCommand;
import com.jobhunter.profiles.application.Commands.ProfileItemsCommand;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.ProfessionalProfile;
import com.jobhunter.profiles.domain.Project;
import com.jobhunter.profiles.domain.UserSkill;
import com.jobhunter.profiles.infrastructure.ExperienceRepository;
import com.jobhunter.profiles.infrastructure.ProfessionalProfileRepository;
import com.jobhunter.profiles.infrastructure.ProjectRepository;
import com.jobhunter.profiles.infrastructure.UserSkillRepository;
import com.jobhunter.shared.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProfessionalProfileService {

    private static final int MAX_PROFILES = 20;

    private final ProfessionalProfileRepository profiles;
    private final ExperienceRepository experiences;
    private final ProjectRepository projects;
    private final UserSkillRepository userSkills;
    private final Clock clock;

    public ProfessionalProfileService(ProfessionalProfileRepository profiles, ExperienceRepository experiences,
                                      ProjectRepository projects, UserSkillRepository userSkills, Clock clock) {
        this.profiles = profiles;
        this.experiences = experiences;
        this.projects = projects;
        this.userSkills = userSkills;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProfessionalProfile> list(long userId) {
        return profiles.findByUserId(userId).stream()
                .sorted(Comparator.comparing(ProfessionalProfile::isDefault).reversed()
                        .thenComparing(ProfessionalProfile::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessionalProfile get(long userId, long id) {
        return profiles.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Perfil"));
    }

    /** The default profile, or the first one, or empty if the user has none. */
    @Transactional(readOnly = true)
    public java.util.Optional<ProfessionalProfile> active(long userId) {
        return list(userId).stream().findFirst();
    }

    @Transactional
    public ProfessionalProfile create(long userId, ProfileCommand c) {
        List<ProfessionalProfile> existing = profiles.findByUserId(userId);
        if (existing.size() >= MAX_PROFILES) {
            throw ApiException.badRequest("LIMIT_REACHED", "Máximo " + MAX_PROFILES + " perfiles profesionales");
        }
        if (existing.stream().anyMatch(p -> p.getName().equalsIgnoreCase(c.name().trim()))) {
            throw ApiException.conflict("Ya existe un perfil con ese nombre");
        }
        ProfessionalProfile p = new ProfessionalProfile(userId);
        apply(p, c);
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(c.isDefault());
        if (makeDefault) existing.forEach(o -> o.setDefault(false));
        p.setDefault(makeDefault);
        return profiles.save(p);
    }

    @Transactional
    public ProfessionalProfile update(long userId, long id, ProfileCommand c) {
        ProfessionalProfile p = get(userId, id);
        p.checkVersion(c.version());
        if (profiles.findByUserId(userId).stream()
                .anyMatch(o -> !o.getId().equals(id) && o.getName().equalsIgnoreCase(c.name().trim()))) {
            throw ApiException.conflict("Ya existe un perfil con ese nombre");
        }
        apply(p, c);
        if (Boolean.TRUE.equals(c.isDefault()) && !p.isDefault()) {
            profiles.findByUserId(userId).forEach(o -> o.setDefault(false));
            p.setDefault(true);
        }
        return p;
    }

    @Transactional
    public void delete(long userId, long id) {
        ProfessionalProfile p = get(userId, id);
        p.markDeleted(clock.instant());
        if (p.isDefault()) {
            p.setDefault(false);
            profiles.findByUserId(userId).stream().filter(o -> !o.getId().equals(id)).findFirst()
                    .ifPresent(o -> o.setDefault(true));
        }
    }

    @Transactional
    public ProfessionalProfile setItems(long userId, long id, ProfileItemsCommand c) {
        ProfessionalProfile p = get(userId, id);
        Set<Long> ownExperiences = experiences.findByUserIdAndIdIn(userId, c.experienceIds()).stream()
                .map(Experience::getId).collect(Collectors.toSet());
        Set<Long> ownProjects = projects.findByUserIdAndIdIn(userId, c.projectIds()).stream()
                .map(Project::getId).collect(Collectors.toSet());
        Set<Long> ownSkills = userSkills.findByUserIdAndSkillIdIn(userId, c.skillIds()).stream()
                .map(UserSkill::getSkillId).collect(Collectors.toSet());
        if (!ownExperiences.containsAll(c.experienceIds()) || !ownProjects.containsAll(c.projectIds())
                || !ownSkills.containsAll(c.skillIds())) {
            throw ApiException.badRequest("UNKNOWN_ITEM", "Solo puedes incluir elementos de tu propio perfil");
        }
        p.setItems(c.experienceIds(), c.projectIds(), c.skillIds());
        return p;
    }

    private static void apply(ProfessionalProfile p, ProfileCommand c) {
        p.update(c.name().trim(), ProfileItemsService.blank(c.headline()), ProfileItemsService.blank(c.summary()),
                ProfileItemsService.blank(c.targetRole()), ProfileItemsService.blank(c.contactEmail()),
                ProfileItemsService.blank(c.phone()), ProfileItemsService.blank(c.city()),
                ProfileItemsService.blank(c.country()), ProfileItemsService.blank(c.githubUrl()),
                ProfileItemsService.blank(c.linkedinUrl()), ProfileItemsService.blank(c.portfolioUrl()),
                ProfileItemsService.blank(c.websiteUrl()));
    }
}
