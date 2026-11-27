package com.jobhunter.applications.api;

import com.jobhunter.applications.application.ApplicationService;
import com.jobhunter.applications.domain.Application;
import com.jobhunter.applications.domain.ApplicationEvent;
import com.jobhunter.applications.domain.ApplicationStatus;
import com.jobhunter.jobs.application.MatchLookup;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.api.PageResponse;
import com.jobhunter.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final ObjectProvider<MatchLookup> matches;

    public ApplicationController(ApplicationService service, ObjectProvider<MatchLookup> matches) {
        this.service = service;
        this.matches = matches;
    }

    public record MatchRef(int scoreTotal, String classification) {
    }

    public record ApplicationDto(Long id, Long jobOfferId, String companyName, String positionTitle, String jobUrl,
                                 String sourceLabel, String status, String method, Long resumeId, Integer resumeVersionNo,
                                 Long coverLetterId, BigDecimal salaryExpectation, String salaryCurrency, String salaryPeriod,
                                 Instant appliedAt, LocalDate followUpAt, String notes, int kanbanOrder, MatchRef match,
                                 Instant createdAt, Instant updatedAt, int version) {
    }

    public record EventDto(Long id, String type, String fromStatus, String toStatus, String note, Instant occurredAt) {
        static EventDto from(ApplicationEvent e) {
            return new EventDto(e.getId(), e.getType(), e.getFromStatus(), e.getToStatus(), e.getNote(), e.getOccurredAt());
        }
    }

    public record CreateRequest(Long jobOfferId, String companyName, String positionTitle, String jobUrl, String sourceLabel,
                                Application.Method method, String notes) {
    }

    public record TransitionRequest(@NotNull ApplicationStatus toStatus, String note) {
    }

    public record ReorderRequest(@NotNull ApplicationStatus status, @NotNull List<Long> orderedIds) {
    }

    public record EventRequest(@NotNull String type, String note, Instant occurredAt) {
    }

    public record SubmitRequest(@NotNull Application.Method method, Instant submittedAt, String note) {
    }

    public record Column(String status, List<ApplicationDto> items) {
    }

    private List<ApplicationDto> dtos(long userId, List<Application> list) {
        List<Long> jobIds = list.stream().map(Application::getJobOfferId).filter(java.util.Objects::nonNull).toList();
        MatchLookup lookup = matches.getIfAvailable();
        Map<Long, MatchLookup.MatchSummary> m = lookup == null || jobIds.isEmpty() ? Map.of() : lookup.forOffers(userId, jobIds);
        return list.stream().map(a -> {
            var s = a.getJobOfferId() == null ? null : m.get(a.getJobOfferId());
            return new ApplicationDto(a.getId(), a.getJobOfferId(), a.getCompanyName(), a.getPositionTitle(), a.getJobUrl(),
                    a.getSourceLabel(), a.getStatus().name(), a.getMethod() == null ? null : a.getMethod().name(), a.getResumeId(),
                    a.getResumeVersionNo(), a.getCoverLetterId(), a.getSalaryExpectation(), a.getSalaryCurrency(), a.getSalaryPeriod(),
                    a.getAppliedAt(), a.getFollowUpAt(), a.getNotes(), a.getKanbanOrder(),
                    s == null ? null : new MatchRef(s.scoreTotal(), s.classification()), a.getCreatedAt(), a.getUpdatedAt(), a.getVersion());
        }).toList();
    }

    private ApplicationDto dto(long userId, Application a) {
        return dtos(userId, List.of(a)).get(0);
    }

    @GetMapping("/transitions")
    public Map<String, List<String>> transitions() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            out.put(s.name(), s.allowedTargets().stream().map(Enum::name).toList());
        }
        return out;
    }

    @GetMapping
    public PageResponse<ApplicationDto> list(@RequestParam(required = false) List<String> status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "updated") String sort,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUser.id();
        Page<Application> p;
        try {
            p = service.list(userId, status, q, sort, page, size);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Estado no válido");
        }
        return new PageResponse<>(dtos(userId, p.getContent()), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @GetMapping("/board")
    public Map<String, List<Column>> board() {
        long userId = CurrentUser.id();
        List<Column> columns = new ArrayList<>();
        service.board(userId).forEach((status, items) -> columns.add(new Column(status.name(), dtos(userId, items))));
        return Map.of("columns", columns);
    }

    @GetMapping("/by-job/{jobId}")
    public ApplicationDto byJob(@PathVariable long jobId) {
        long userId = CurrentUser.id();
        return dto(userId, service.byJob(userId, jobId).orElseThrow(() -> ApiException.notFound("Postulación")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDto create(@RequestBody CreateRequest body) {
        long userId = CurrentUser.id();
        Application a = body.jobOfferId() != null ? service.createFromJob(userId, body.jobOfferId())
                : service.createManual(userId, body.companyName(), body.positionTitle(), body.jobUrl(), body.sourceLabel(),
                body.method(), body.notes());
        return dto(userId, a);
    }

    @GetMapping("/{id}")
    public ApplicationDto get(@PathVariable long id) {
        long userId = CurrentUser.id();
        return dto(userId, service.get(userId, id));
    }

    @PatchMapping("/{id}")
    public ApplicationDto update(@PathVariable long id, @RequestBody ApplicationService.UpdateCommand body) {
        long userId = CurrentUser.id();
        return dto(userId, service.update(userId, id, body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/transition")
    public ApplicationDto transition(@PathVariable long id, @Valid @RequestBody TransitionRequest body) {
        long userId = CurrentUser.id();
        return dto(userId, service.transition(userId, id, body.toStatus(), body.note()));
    }

    @PutMapping("/kanban-order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ReorderRequest body) {
        service.reorder(CurrentUser.id(), body.status(), body.orderedIds());
    }

    @GetMapping("/{id}/events")
    public List<EventDto> events(@PathVariable long id) {
        return service.events(CurrentUser.id(), id).stream().map(EventDto::from).toList();
    }

    @PostMapping("/{id}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto addEvent(@PathVariable long id, @Valid @RequestBody EventRequest body) {
        return EventDto.from(service.addEvent(CurrentUser.id(), id, body.type(), body.note(), body.occurredAt()));
    }

    @PostMapping("/{id}/prepare")
    public ApplicationService.Prepared prepare(@PathVariable long id) {
        return service.prepare(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/mark-submitted")
    public ApplicationDto markSubmitted(@PathVariable long id, @Valid @RequestBody SubmitRequest body) {
        long userId = CurrentUser.id();
        return dto(userId, service.markSubmitted(userId, id, body.method(), body.submittedAt(), body.note()));
    }
}
