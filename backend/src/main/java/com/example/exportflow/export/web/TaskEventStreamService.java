package com.example.exportflow.export.web;

import com.example.exportflow.export.infrastructure.progress.TaskProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskEventStreamService {
    private static final Logger log = LoggerFactory.getLogger(TaskEventStreamService.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TaskEventStreamService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(error -> emitters.remove(id));
        send(id, emitter, "connected", Map.of("connected", true));
        return emitter;
    }

    public void broadcast(String json) {
        try {
            TaskProgressEvent event = objectMapper.readValue(json, TaskProgressEvent.class);
            emitters.forEach((id, emitter) -> send(id, emitter, event.eventType(), event));
        } catch (Exception exception) {
            log.warn("Cannot broadcast task event: {}", exception.getMessage());
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        Map<String, Object> data = Map.of("time", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        emitters.forEach((id, emitter) -> send(id, emitter, "heartbeat", data));
    }

    private void send(String id, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(id);
            emitter.complete();
        }
    }
}

