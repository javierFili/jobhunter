package com.jobhunter.profiles;

import com.jobhunter.profiles.domain.ExperienceYears;
import com.jobhunter.profiles.domain.ExperienceYears.Period;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceYearsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void sumsDisjointPeriods() {
        BigDecimal years = ExperienceYears.of(List.of(
                new Period(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)),
                new Period(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1))), TODAY);
        assertThat(years).isEqualByComparingTo("2.0");
    }

    @Test
    void doesNotDoubleCountOverlappingJobs() {
        BigDecimal years = ExperienceYears.of(List.of(
                new Period(LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1)),
                new Period(LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1))), TODAY);
        assertThat(years).isEqualByComparingTo("2.0");
    }

    @Test
    void currentJobCountsUntilToday() {
        BigDecimal years = ExperienceYears.of(List.of(new Period(LocalDate.of(2025, 11, 26), null)), TODAY);
        assertThat(years).isEqualByComparingTo("1.0");
    }

    @Test
    void futureEndDateIsCappedAtToday() {
        BigDecimal years = ExperienceYears.of(List.of(
                new Period(LocalDate.of(2025, 11, 26), LocalDate.of(2030, 1, 1))), TODAY);
        assertThat(years).isEqualByComparingTo("1.0");
    }

    @Test
    void emptyIsZero() {
        assertThat(ExperienceYears.of(List.of(), TODAY)).isEqualByComparingTo("0");
    }
}
