package com.jobhunter.shared.audit;

import com.jobhunter.shared.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** Writes security-relevant events. Never pass secrets or CV content in {@code details}. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorUserId, String action, String targetType, Object targetId, Map<String, ?> details) {
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : request.getRemoteAddr();
        String ua = request == null ? null : request.getHeader("User-Agent");
        repository.save(new AuditLog(actorUserId, action, targetType, targetId == null ? null : String.valueOf(targetId),
                ip, ua, details == null || details.isEmpty() ? null : Json.write(details)));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
    }
}
