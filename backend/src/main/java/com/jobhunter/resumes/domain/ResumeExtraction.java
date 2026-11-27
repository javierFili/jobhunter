package com.jobhunter.resumes.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resume_extractions")
public class ResumeExtraction extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "file_id", nullable = false, updatable = false)
    private Long fileId;
    @Column(nullable = false, length = 16)
    private String extractor;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "raw_text", columnDefinition = "mediumtext")
    private String rawText;
    @Column(columnDefinition = "json")
    private String parsed;
    @Column(length = 16)
    private String parser;
    @Column(length = 500)
    private String error;
    @Column(name = "imported_at")
    private Instant importedAt;

    protected ResumeExtraction() {
    }

    public ResumeExtraction(Long userId, Long fileId, String extractor, String status, String rawText, String parsed,
                            String parser, String error) {
        this.userId = userId;
        this.fileId = fileId;
        this.extractor = extractor;
        this.status = status;
        this.rawText = rawText;
        this.parsed = parsed;
        this.parser = parser;
        this.error = error;
    }

    public void replaceParsed(String parsed, String parser) {
        this.parsed = parsed;
        this.parser = parser;
    }

    public void markImported(Instant now) { this.importedAt = now; }

    public Long getUserId() { return userId; }
    public Long getFileId() { return fileId; }
    public String getExtractor() { return extractor; }
    public String getStatus() { return status; }
    public String getRawText() { return rawText; }
    public String getParsed() { return parsed; }
    public String getParser() { return parser; }
    public String getError() { return error; }
    public Instant getImportedAt() { return importedAt; }
}
