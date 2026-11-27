package com.jobhunter.profiles.domain;

import com.jobhunter.profiles.domain.Enums.SkillCategory;
import com.jobhunter.shared.domain.BaseEntity;
import com.jobhunter.shared.json.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Shared technology catalogue entry (not user-owned). */
@Entity
@Table(name = "skills")
public class Skill extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SkillCategory category;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "json")
    private List<String> aliases = new ArrayList<>();

    protected Skill() {
    }

    public Skill(String name, SkillCategory category) {
        this.name = name.trim();
        this.normalizedName = normalize(name);
        this.category = category;
    }

    /** Lower-case, trimmed, single-spaced. Keeps symbols so "c#", "c++" and ".net" stay distinct. */
    public static String normalize(String name) {
        return name == null ? null : name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public SkillCategory getCategory() { return category; }
    public List<String> getAliases() { return aliases; }
}
