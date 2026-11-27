package com.jobhunter.applications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_events")
public class ApplicationEvent {

    public static final java.util.Set<String> USER_TYPES = java.util.Set.of("NOTE", "MESSAGE_RECEIVED", "INTERVIEW_SCHEDULED", "FOLLOW_UP");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(nullable = false, length = 24) private String type;
    @Column(name = "from_status", length = 20) private String fromStatus;
    @Column(name = "to_status", length = 20) private String toStatus;
    @Column(columnDefinition = "text") private String note;
    @Column(columnDefinition = "json") private String payload;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected ApplicationEvent() {
    }

    public ApplicationEvent(Long applicationId, String type, String fromStatus, String toStatus, String note, String payload,
                            Instant occurredAt, Long createdBy) {
        this.applicationId = applicationId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getType() { return type; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
