package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserId(Long userId);

    Optional<UserSkill> findByUserIdAndSkillId(Long userId, Long skillId);

    List<UserSkill> findByUserIdAndSkillIdIn(Long userId, Collection<Long> skillIds);

    long countByUserId(Long userId);
}
