package com.jobhunter.profiles.api;

import com.jobhunter.profiles.api.ProfileDtos.CatalogSkillDto;
import com.jobhunter.profiles.api.ProfileDtos.LanguageDto;
import com.jobhunter.profiles.api.ProfileDtos.PreferencesDto;
import com.jobhunter.profiles.api.ProfileDtos.ProfileDto;
import com.jobhunter.profiles.application.Commands.CatalogSkillCommand;
import com.jobhunter.profiles.application.Commands.LanguageCommand;
import com.jobhunter.profiles.application.Commands.PreferencesCommand;
import com.jobhunter.profiles.application.Commands.ProfileCommand;
import com.jobhunter.profiles.application.Commands.ProfileItemsCommand;
import com.jobhunter.profiles.application.Commands.UserSkillCommand;
import com.jobhunter.profiles.application.Commands.VerifyCommand;
import com.jobhunter.profiles.application.ProfessionalProfileService;
import com.jobhunter.profiles.application.ProfileService;
import com.jobhunter.profiles.application.SkillService;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ProfileController {

    private final ProfessionalProfileService profiles;
    private final ProfileService profileService;
    private final SkillService skills;

    public ProfileController(ProfessionalProfileService profiles, ProfileService profileService, SkillService skills) {
        this.profiles = profiles;
        this.profileService = profileService;
        this.skills = skills;
    }

    // professional profiles
    @GetMapping("/profiles")
    public List<ProfileDto> profiles() {
        return profiles.list(CurrentUser.id()).stream().map(ProfileDto::from).toList();
    }

    @GetMapping("/profiles/{id}")
    public ProfileDto profile(@PathVariable long id) {
        return ProfileDto.from(profiles.get(CurrentUser.id(), id));
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileDto create(@Valid @RequestBody ProfileCommand body) {
        return ProfileDto.from(profiles.create(CurrentUser.id(), body));
    }

    @PutMapping("/profiles/{id}")
    public ProfileDto update(@PathVariable long id, @Valid @RequestBody ProfileCommand body) {
        return ProfileDto.from(profiles.update(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        profiles.delete(CurrentUser.id(), id);
    }

    @PutMapping("/profiles/{id}/items")
    public ProfileDto items(@PathVariable long id, @Valid @RequestBody ProfileItemsCommand body) {
        return ProfileDto.from(profiles.setItems(CurrentUser.id(), id, body));
    }

    @PostMapping("/profile/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@RequestBody VerifyCommand body) {
        profileService.verify(CurrentUser.id(), body);
    }

    // skills
    @GetMapping("/catalog/skills")
    public List<CatalogSkillDto> catalog(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "20") int limit) {
        return skills.search(q, limit).stream().map(CatalogSkillDto::from).toList();
    }

    @PostMapping("/catalog/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogSkillDto createCatalogSkill(@Valid @RequestBody CatalogSkillCommand body) {
        return CatalogSkillDto.from(skills.createOrGet(body.name(), body.category()));
    }

    @GetMapping("/skills")
    public List<SkillService.UserSkillView> mySkills() {
        return skills.list(CurrentUser.id());
    }

    @PutMapping("/skills/{skillId}")
    public SkillService.UserSkillView upsertSkill(@PathVariable long skillId, @Valid @RequestBody UserSkillCommand body) {
        return skills.upsert(CurrentUser.id(), skillId, body.evidenceLevel(), body.selfRating(), body.lastUsedYear());
    }

    @DeleteMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable long skillId) {
        skills.delete(CurrentUser.id(), skillId);
    }

    // languages
    @GetMapping("/languages")
    public List<LanguageDto> languages() {
        return profileService.listLanguages(CurrentUser.id()).stream().map(LanguageDto::from).toList();
    }

    @PutMapping("/languages")
    public List<LanguageDto> replaceLanguages(@RequestBody List<@Valid LanguageCommand> body) {
        return profileService.replaceLanguages(CurrentUser.id(), body).stream().map(LanguageDto::from).toList();
    }

    // preferences
    @GetMapping("/preferences")
    public PreferencesDto preferences() {
        return PreferencesDto.from(profileService.getPreferences(CurrentUser.id()));
    }

    @PutMapping("/preferences")
    public PreferencesDto updatePreferences(@Valid @RequestBody PreferencesCommand body) {
        return PreferencesDto.from(profileService.updatePreferences(CurrentUser.id(), body));
    }
}
