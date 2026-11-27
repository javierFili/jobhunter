package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.SalaryPeriod;
import com.jobhunter.profiles.domain.Enums.Seniority;
import com.jobhunter.shared.domain.BaseEntity;
import com.jobhunter.shared.json.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** What the user is looking for; used by deterministic matching (location, salary, contract...). */
@Entity
@Table(name = "job_preferences")
public class JobPreferences extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "json")
    private List<String> regions = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "countries_include", columnDefinition = "json")
    private List<String> countriesInclude = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "countries_exclude", columnDefinition = "json")
    private List<String> countriesExclude = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "json")
    private List<String> modalities = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "contract_types", columnDefinition = "json")
    private List<String> contractTypes = new ArrayList<>();
    @Convert(converter = StringListConverter.class)
    @Column(name = "engagement_types", columnDefinition = "json")
    private List<String> engagementTypes = new ArrayList<>();
    @Column(name = "min_salary", precision = 12, scale = 2)
    private BigDecimal minSalary;
    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 8)
    private SalaryPeriod salaryPeriod;
    @Convert(converter = StringListConverter.class)
    @Column(name = "work_languages", columnDefinition = "json")
    private List<String> workLanguages = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Seniority seniority;
    @Column(name = "relocation_ok", nullable = false)
    private boolean relocationOk;

    protected JobPreferences() {
    }

    public JobPreferences(Long userId) {
        this.userId = userId;
    }

    public void update(List<String> regions, List<String> countriesInclude, List<String> countriesExclude,
                       List<String> modalities, List<String> contractTypes, List<String> engagementTypes,
                       BigDecimal minSalary, String salaryCurrency, SalaryPeriod salaryPeriod,
                       List<String> workLanguages, Seniority seniority, boolean relocationOk) {
        this.regions = copy(regions);
        this.countriesInclude = copy(countriesInclude);
        this.countriesExclude = copy(countriesExclude);
        this.modalities = copy(modalities);
        this.contractTypes = copy(contractTypes);
        this.engagementTypes = copy(engagementTypes);
        this.minSalary = minSalary;
        this.salaryCurrency = salaryCurrency;
        this.salaryPeriod = salaryPeriod;
        this.workLanguages = copy(workLanguages);
        this.seniority = seniority;
        this.relocationOk = relocationOk;
    }

    private static List<String> copy(List<String> in) {
        return in == null ? new ArrayList<>() : new ArrayList<>(in.stream().distinct().toList());
    }

    public Long getUserId() { return userId; }
    public List<String> getRegions() { return regions; }
    public List<String> getCountriesInclude() { return countriesInclude; }
    public List<String> getCountriesExclude() { return countriesExclude; }
    public List<String> getModalities() { return modalities; }
    public List<String> getContractTypes() { return contractTypes; }
    public List<String> getEngagementTypes() { return engagementTypes; }
    public BigDecimal getMinSalary() { return minSalary; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public List<String> getWorkLanguages() { return workLanguages; }
    public Seniority getSeniority() { return seniority; }
    public boolean isRelocationOk() { return relocationOk; }
}
