package com.jobhunter.notifications.domain;

import com.jobhunter.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 1000) private String body;
    @Column(length = 300) private String link;
    @Column(columnDefinition = "json") private String payload;
    @Column(name = "read_at") private Instant readAt;

    protected Notification() {
    }

    public Notification(Long userId, String type, String title, String body, String link, String payload) {
        this.userId = userId;
        this.type = type;
        this.title = title.length() > 200 ? title.substring(0, 200) : title;
        this.body = body == null || body.length() <= 1000 ? body : body.substring(0, 1000);
        this.link = link;
        this.payload = payload;
    }

    public void markRead(Instant now) { if (readAt == null) readAt = now; }

    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public Instant getReadAt() { return readAt; }
}
