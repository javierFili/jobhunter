package com.jobhunter.shared.files;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "stored_files")
public class StoredFile extends BaseEntity {

    public enum Purpose { CV_UPLOAD, RESUME_PDF, COVER_LETTER_PDF, EXPORT }

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "storage_key", nullable = false, length = 36, updatable = false)
    private String storageKey;
    @Column(name = "original_name", nullable = false)
    private String originalName;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, columnDefinition = "binary(32)")
    private byte[] sha256;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Purpose purpose;

    protected StoredFile() {
    }

    StoredFile(Long userId, String storageKey, String originalName, String contentType, long sizeBytes, byte[] sha256,
               Purpose purpose) {
        this.userId = userId;
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.purpose = purpose;
    }

    public Long getUserId() { return userId; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getSha256() { return sha256; }
    public Purpose getPurpose() { return purpose; }
}
