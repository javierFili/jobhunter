package com.jobhunter.resumes.infrastructure;

import com.jobhunter.resumes.domain.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

    List<ResumeVersion> findByResumeIdOrderByVersionNoDesc(Long resumeId);

    Optional<ResumeVersion> findByResumeIdAndVersionNo(Long resumeId, int versionNo);

    Optional<ResumeVersion> findFirstByResumeIdAndJobOfferIdAndOriginOrderByVersionNoDesc(Long resumeId, Long jobOfferId,
                                                                                ResumeVersion.Origin origin);

    @Query("select coalesce(max(v.versionNo), 0) from ResumeVersion v where v.resumeId = :resumeId")
    int maxVersionNo(Long resumeId);
}
