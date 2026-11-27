package com.jobhunter.shared.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByIdAndUserId(Long id, Long userId);

    java.util.List<StoredFile> findByUserId(Long userId);
}
