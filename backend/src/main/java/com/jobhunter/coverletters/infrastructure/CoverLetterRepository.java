package com.jobhunter.coverletters.infrastructure;

import com.jobhunter.coverletters.domain.CoverLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    Optional<CoverLetter> findByIdAndUserId(Long id, Long userId);

    List<CoverLetter> findByLetterGroupIdOrderByVersionNoDesc(Long letterGroupId);

    /** Latest version of each letter group (optionally for one job). */
    @Query("""
            select c from CoverLetter c where c.userId = :userId and (:jobId is null or c.jobOfferId = :jobId)
              and (:format is null or c.format = :format) and (:status is null or c.status = :status)
              and c.versionNo = (select max(d.versionNo) from CoverLetter d where d.letterGroupId = c.letterGroupId)
            order by c.createdAt desc""")
    Page<CoverLetter> latestPerGroup(Long userId, Long jobId, String format, String status, Pageable pageable);

    @Query("select coalesce(max(c.versionNo), 0) from CoverLetter c where c.letterGroupId = :groupId")
    int maxVersion(Long groupId);

    @Modifying
    @Query("delete from CoverLetter c where c.letterGroupId = :groupId and c.userId = :userId")
    int deleteGroup(Long groupId, Long userId);
}
