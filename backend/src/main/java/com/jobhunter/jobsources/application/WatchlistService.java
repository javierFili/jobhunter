package com.jobhunter.jobsources.application;

import com.jobhunter.jobsources.domain.CompanyWatch;
import com.jobhunter.jobsources.infrastructure.AshbyConnector;
import com.jobhunter.jobsources.infrastructure.CompanyWatchRepository;
import com.jobhunter.jobsources.infrastructure.GreenhouseConnector;
import com.jobhunter.jobsources.infrastructure.LeverConnector;
import com.jobhunter.shared.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class WatchlistService {

    private static final Set<String> ATS = Set.of("GREENHOUSE", "LEVER", "ASHBY");
    private static final int MAX_WATCHES = 200;

    private final CompanyWatchRepository repository;
    private final GreenhouseConnector greenhouse;
    private final LeverConnector lever;
    private final AshbyConnector ashby;

    public WatchlistService(CompanyWatchRepository repository, GreenhouseConnector greenhouse, LeverConnector lever,
                            AshbyConnector ashby) {
        this.repository = repository;
        this.greenhouse = greenhouse;
        this.lever = lever;
        this.ashby = ashby;
    }

    @Transactional(readOnly = true)
    public List<CompanyWatch> list(long userId) {
        return repository.findByUserIdOrderByAtsAscBoardTokenAsc(userId);
    }

    @Transactional
    public CompanyWatch add(long userId, String ats, String token, String companyName) {
        String a = validate(ats, token);
        if (repository.findByUserIdOrderByAtsAscBoardTokenAsc(userId).size() >= MAX_WATCHES) {
            throw ApiException.badRequest("LIMIT_REACHED", "Máximo " + MAX_WATCHES + " empresas seguidas");
        }
        if (repository.existsByUserIdAndAtsAndBoardToken(userId, a, token)) {
            throw ApiException.conflict("Ya sigues esa empresa");
        }
        return repository.save(new CompanyWatch(userId, a, token.trim(),
                companyName == null || companyName.isBlank() ? null : companyName.trim()));
    }

    @Transactional
    public CompanyWatch update(long userId, long id, Boolean enabled, String companyName) {
        CompanyWatch w = repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Empresa"));
        w.update(enabled, companyName);
        return w;
    }

    @Transactional
    public void delete(long userId, long id) {
        repository.delete(repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Empresa")));
    }

    public record Validation(boolean ok, int jobCount, String message) {
    }

    /** One request to the public board to check the token exists. */
    public Validation check(String ats, String token) {
        String a = validate(ats, token);
        try {
            int count = switch (a) {
                case "GREENHOUSE" -> greenhouse.countJobs(token);
                case "LEVER" -> lever.countJobs(token);
                default -> ashby.countJobs(token);
            };
            return count < 0 ? new Validation(false, 0, "No existe un tablero con ese token")
                    : new Validation(true, count, count + " ofertas publicadas");
        } catch (RuntimeException e) {
            return new Validation(false, 0, "No se pudo verificar: " + e.getMessage());
        }
    }

    private static String validate(String ats, String token) {
        String a = ats == null ? "" : ats.toUpperCase();
        if (!ATS.contains(a)) throw ApiException.badRequest("VALIDATION_FAILED", "ATS debe ser GREENHOUSE, LEVER o ASHBY");
        if (token == null || !token.trim().matches(CompanyWatch.TOKEN_PATTERN)) {
            throw ApiException.badRequest("INVALID_TOKEN", "Token inválido: usa solo letras, números, '.', '_' o '-'");
        }
        return a;
    }
}
