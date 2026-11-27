package com.jobhunter.profiles.infrastructure;

import com.jobhunter.profiles.domain.UserLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserLanguageRepository extends JpaRepository<UserLanguage, Long> {

    List<UserLanguage> findByUserIdOrderByLanguageCode(Long userId);

    long countByUserId(Long userId);
}
