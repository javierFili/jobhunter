package com.jobhunter.jobs.infrastructure;

import com.jobhunter.jobs.domain.UserJobState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface UserJobStateRepository extends JpaRepository<UserJobState, UserJobState.Id> {

    @Query("select s from UserJobState s where s.id.userId = :userId and s.id.jobOfferId in :offerIds")
    List<UserJobState> findForUser(Long userId, Collection<Long> offerIds);

    @Query("select count(s) from UserJobState s where s.id.userId = :userId and s.saved = true")
    long countSaved(Long userId);
}
