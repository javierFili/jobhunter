package com.jobhunter.jobs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Admin-entered exchange rate (units of USD per 1 unit of currency). */
@Entity
@Table(name = "fx_rates")
public class FxRate {

    @Embeddable
    public record Id(@Column(name = "currency") String currency, @Column(name = "as_of") LocalDate asOf)
            implements Serializable {
    }

    @EmbeddedId
    private Id id;
    @Column(name = "rate_to_usd", nullable = false, precision = 18, scale = 8)
    private BigDecimal rateToUsd;

    protected FxRate() {
    }

    public FxRate(String currency, LocalDate asOf, BigDecimal rateToUsd) {
        this.id = new Id(currency, asOf);
        this.rateToUsd = rateToUsd;
    }

    public void setRate(BigDecimal rate) { this.rateToUsd = rate; }

    public Id getId() { return id; }
    public BigDecimal getRateToUsd() { return rateToUsd; }
}
