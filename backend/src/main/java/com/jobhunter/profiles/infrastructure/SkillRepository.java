package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.Skill;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNormalizedName(String normalizedName);

    @Query("select s from Skill s where s.normalizedName like concat(:prefix, '%') order by length(s.name), s.name")
    List<Skill> searchByPrefix(String prefix, Pageable pageable);

    @Query("select s from Skill s where s.normalizedName like concat('%', :q, '%') order by length(s.name), s.name")
    List<Skill> searchContaining(String q, Pageable pageable);
}
