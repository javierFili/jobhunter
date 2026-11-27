package com.jobhunter.users.infrastructure;

import com.jobhunter.users.domain.ConsentType;
import com.jobhunter.users.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserId(Long userId);

    Optional<UserConsent> findByUserIdAndConsentTypeAndProvider(Long userId, ConsentType type, String provider);
}
