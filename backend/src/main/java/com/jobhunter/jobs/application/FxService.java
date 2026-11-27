package com.jobhunter.jobs.application;

import com.jobhunter.jobs.domain.FxRate;
import com.jobhunter.jobs.domain.SalaryParser;
import com.jobhunter.jobs.infrastructure.FxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Salary normalization to USD/month using admin-entered rates only (no external FX service). */
@Service
public class FxService {

    private final FxRateRepository repository;

    public FxService(FxRateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> rates() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", BigDecimal.ONE);
        repository.latest().forEach(r -> rates.put(r.getId().currency(), r.getRateToUsd()));
        return rates;
    }

    /** Null when currency/period is unknown or no rate exists (the job is then not filtered by salary). */
    public static BigDecimal usdPerMonth(BigDecimal amount, String currency, String period, Map<String, BigDecimal> rates) {
        if (amount == null || currency == null) return null;
        BigDecimal monthly = SalaryParser.monthly(amount, period);
        BigDecimal rate = rates.get(currency);
        if (monthly == null || rate == null) return null;
        return monthly.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<FxRate> latest() {
        return repository.latest();
    }

    @Transactional
    public void upsert(String currency, BigDecimal rateToUsd, LocalDate asOf) {
        FxRate.Id id = new FxRate.Id(currency, asOf);
        repository.findById(id).ifPresentOrElse(r -> r.setRate(rateToUsd),
                () -> repository.save(new FxRate(currency, asOf, rateToUsd)));
    }
}
