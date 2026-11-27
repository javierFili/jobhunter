package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.jobsources.domain.ApifyActor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApifyActorRepository extends JpaRepository<ApifyActor, Long> {

    boolean existsByActorId(String actorId);
}
