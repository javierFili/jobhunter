package com.jobhunter.shared.tasks;

import com.jobhunter.shared.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks background AI tasks (bounded "ai" executor) so the UI can poll a state even when a task fails before
 * reaching the provider (no consent, no budget...). In-memory: state is lost on restart, results are in the DB.
 */
@Component
public class AsyncTasks {

    private static final Logger log = LoggerFactory.getLogger(AsyncTasks.class);
    private static final int MAX_ENTRIES = 5_000;

    public enum State { RUNNING, SUCCEEDED, FAILED }

    public record Status(State state, String code, String message, Instant updatedAt) {
    }

    private final TaskExecutor executor;
    private final Map<String, Status> states = new ConcurrentHashMap<>();

    public AsyncTasks(@Qualifier("aiExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void submit(String key, Runnable work) {
        Status current = states.get(key);
        if (current != null && current.state() == State.RUNNING) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RUNNING", "La tarea ya está en curso");
        }
        if (states.size() > MAX_ENTRIES) states.clear();
        states.put(key, new Status(State.RUNNING, null, null, Instant.now()));
        try {
            executor.execute(() -> {
                try {
                    work.run();
                    states.put(key, new Status(State.SUCCEEDED, null, null, Instant.now()));
                } catch (ApiException e) {
                    states.put(key, new Status(State.FAILED, e.code(), e.getMessage(), Instant.now()));
                } catch (RuntimeException e) {
                    log.error("Background task {} failed", key, e);
                    states.put(key, new Status(State.FAILED, "INTERNAL_ERROR", e.getMessage(), Instant.now()));
                }
            });
        } catch (TaskRejectedException e) {
            states.remove(key);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "Hay demasiadas tareas de IA en cola; intenta más tarde");
        }
    }

    public Optional<Status> status(String key) {
        return Optional.ofNullable(states.get(key));
    }
}
