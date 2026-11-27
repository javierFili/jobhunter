package com.jobhunter.profiles.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic "years of professional experience" computed from employment dates, merging overlapping
 * periods so two simultaneous jobs are not counted twice. Never taken from free text or AI.
 */
public final class ExperienceYears {

    private ExperienceYears() {
    }

    public record Period(LocalDate start, LocalDate end) {
    }

    public static BigDecimal of(List<Period> periods, LocalDate today) {
        List<Period> sorted = new ArrayList<>(periods.stream()
                .filter(p -> p.start() != null)
                .map(p -> new Period(p.start(), p.end() == null || p.end().isAfter(today) ? today : p.end()))
                .filter(p -> !p.end().isBefore(p.start()))
                .toList());
        sorted.sort(Comparator.comparing(Period::start));
        long days = 0;
        LocalDate curStart = null;
        LocalDate curEnd = null;
        for (Period p : sorted) {
            if (curStart == null) {
                curStart = p.start();
                curEnd = p.end();
            } else if (!p.start().isAfter(curEnd)) {
                if (p.end().isAfter(curEnd)) curEnd = p.end();
            } else {
                days += ChronoUnit.DAYS.between(curStart, curEnd);
                curStart = p.start();
                curEnd = p.end();
            }
        }
        if (curStart != null) {
            days += ChronoUnit.DAYS.between(curStart, curEnd);
        }
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365.25), 1, RoundingMode.HALF_UP);
    }
}
