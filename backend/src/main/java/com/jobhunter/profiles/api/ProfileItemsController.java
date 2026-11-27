package com.jobhunter.profiles.api;

import com.jobhunter.profiles.api.ProfileDtos.CertificationDto;
import com.jobhunter.profiles.api.ProfileDtos.EducationDto;
import com.jobhunter.profiles.api.ProfileDtos.ExperienceDto;
import com.jobhunter.profiles.api.ProfileDtos.ProjectDto;
import com.jobhunter.profiles.application.Commands.CertificationCommand;
import com.jobhunter.profiles.application.Commands.EducationCommand;
import com.jobhunter.profiles.application.Commands.ExperienceCommand;
import com.jobhunter.profiles.application.Commands.ProjectCommand;
import com.jobhunter.profiles.application.ProfileItemsService;
import com.jobhunter.profiles.application.SkillService;
import com.jobhunter.profiles.domain.Enums.ItemSource;
import com.jobhunter.profiles.domain.Experience;
import com.jobhunter.profiles.domain.Project;
import com.jobhunter.profiles.domain.Skill;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1")
public class ProfileItemsController {

    private final ProfileItemsService service;
    private final SkillService skills;

    public ProfileItemsController(ProfileItemsService service, SkillService skills) {
        this.service = service;
        this.skills = skills;
    }

    // experiences
    @GetMapping("/experiences")
    public List<ExperienceDto> experiences() {
        List<Experience> list = service.listExperiences(CurrentUser.id());
        Map<Long, Skill> cat = skills.byIds(list.stream().flatMap(e -> e.getSkillIds().stream()).toList());
        return list.stream().map(e -> ExperienceDto.from(e, cat)).toList();
    }

    @GetMapping("/experiences/{id}")
    public ExperienceDto experience(@PathVariable long id) {
        return toDto(service.getExperience(CurrentUser.id(), id));
    }

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public ExperienceDto createExperience(@Valid @RequestBody ExperienceCommand body) {
        return toDto(service.createExperience(CurrentUser.id(), body, ItemSource.MANUAL));
    }

    @PutMapping("/experiences/{id}")
    public ExperienceDto updateExperience(@PathVariable long id, @Valid @RequestBody ExperienceCommand body) {
        return toDto(service.updateExperience(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/experiences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@PathVariable long id) {
        service.deleteExperience(CurrentUser.id(), id);
    }

    // projects
    @GetMapping("/projects")
    public List<ProjectDto> projects() {
        List<Project> list = service.listProjects(CurrentUser.id());
        Map<Long, Skill> cat = skills.byIds(list.stream().flatMap(p -> p.getSkillIds().stream()).toList());
        return list.stream().map(p -> ProjectDto.from(p, cat)).toList();
    }

    @GetMapping("/projects/{id}")
    public ProjectDto project(@PathVariable long id) {
        return toDto(service.getProject(CurrentUser.id(), id));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto createProject(@Valid @RequestBody ProjectCommand body) {
        return toDto(service.createProject(CurrentUser.id(), body, ItemSource.MANUAL));
    }

    @PutMapping("/projects/{id}")
    public ProjectDto updateProject(@PathVariable long id, @Valid @RequestBody ProjectCommand body) {
        return toDto(service.updateProject(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable long id) {
        service.deleteProject(CurrentUser.id(), id);
    }

    // educations
    @GetMapping("/educations")
    public List<EducationDto> educations() {
        return service.listEducations(CurrentUser.id()).stream().map(EducationDto::from).toList();
    }

    @GetMapping("/educations/{id}")
    public EducationDto education(@PathVariable long id) {
        return EducationDto.from(service.getEducation(CurrentUser.id(), id));
    }

    @PostMapping("/educations")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationDto createEducation(@Valid @RequestBody EducationCommand body) {
        return EducationDto.from(service.createEducation(CurrentUser.id(), body, ItemSource.MANUAL));
    }

    @PutMapping("/educations/{id}")
    public EducationDto updateEducation(@PathVariable long id, @Valid @RequestBody EducationCommand body) {
        return EducationDto.from(service.updateEducation(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/educations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEducation(@PathVariable long id) {
        service.deleteEducation(CurrentUser.id(), id);
    }

    // certifications
    @GetMapping("/certifications")
    public List<CertificationDto> certifications() {
        return service.listCertifications(CurrentUser.id()).stream().map(CertificationDto::from).toList();
    }

    @GetMapping("/certifications/{id}")
    public CertificationDto certification(@PathVariable long id) {
        return CertificationDto.from(service.getCertification(CurrentUser.id(), id));
    }

    @PostMapping("/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationDto createCertification(@Valid @RequestBody CertificationCommand body) {
        return CertificationDto.from(service.createCertification(CurrentUser.id(), body, ItemSource.MANUAL));
    }

    @PutMapping("/certifications/{id}")
    public CertificationDto updateCertification(@PathVariable long id, @Valid @RequestBody CertificationCommand body) {
        return CertificationDto.from(service.updateCertification(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/certifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertification(@PathVariable long id) {
        service.deleteCertification(CurrentUser.id(), id);
    }

    private ExperienceDto toDto(Experience e) {
        return ExperienceDto.from(e, skills.byIds(e.getSkillIds()));
    }

    private ProjectDto toDto(Project p) {
        return ProjectDto.from(p, skills.byIds(p.getSkillIds()));
    }

}
