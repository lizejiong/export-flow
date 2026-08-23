package com.example.exportflow.export.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/export-tasks")
public class TaskEventController {
    private final TaskEventStreamService streamService;

    public TaskEventController(TaskEventStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return streamService.subscribe();
    }
}
