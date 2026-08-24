package com.example.exportflow.export;

import com.example.exportflow.export.domain.AttemptStatus;
import com.example.exportflow.export.domain.ExportTaskAttempt;
import com.example.exportflow.export.infrastructure.ExportAttemptMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Sql(scripts = "/export-attempt-mapper-schema.sql")
class ExportAttemptMapperIntegrationTest {

    @Autowired
    private ExportAttemptMapper mapper;

    @Test
    void insertsImmutableAttemptWithoutWritingGeneratedIdBackIntoRecord() {
        LocalDateTime now = LocalDateTime.now();
        ExportTaskAttempt attempt = new ExportTaskAttempt(
                null, 99L, 10L, 1, "token-1", "worker-1", AttemptStatus.PROCESSING,
                now, now, null, null, null
        );

        assertThat(mapper.insert(attempt)).isEqualTo(1);
        assertThat(mapper.findByRunId(10L)).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isNotNull();
            assertThat(saved.attemptNo()).isEqualTo(1);
            assertThat(saved.executionToken()).isEqualTo("token-1");
        });
    }
}
