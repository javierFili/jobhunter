package com.jobhunter.notifications.application;

import com.jobhunter.notifications.domain.Notification;
import com.jobhunter.notifications.infrastructure.NotificationRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.json.Json;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/** In-app notifications (no e-mail: sending e-mails is out of scope by user decision). */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void notify(long userId, String type, String title, String body, String link, Map<String, ?> payload) {
        repository.save(new Notification(userId, type, title, body, link, payload == null ? null : Json.write(payload)));
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(long userId, boolean unreadOnly, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return unreadOnly ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, p)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, p);
    }

    @Transactional(readOnly = true)
    public long unread(long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(long userId, long id) {
        repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Notificación"))
                .markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(long userId) {
        repository.markAllRead(userId, clock.instant());
    }
}
