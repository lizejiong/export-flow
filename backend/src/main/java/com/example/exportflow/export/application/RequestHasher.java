package com.example.exportflow.export.application;

import com.example.exportflow.export.domain.ExportType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class RequestHasher {
    private final ObjectMapper objectMapper;

    public RequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalRequest canonicalize(ExportType type, List<Long> selectedIds, FilterSnapshot snapshot) {
        List<Long> ids = selectedIds == null ? List.of() : selectedIds.stream().distinct().sorted().toList();
        return new CanonicalRequest(type, ids, snapshot);
    }

    public String hash(CanonicalRequest request) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash export request", exception);
        }
    }

    public record CanonicalRequest(ExportType exportType, List<Long> selectedOrderIds, FilterSnapshot filters) {}
}

