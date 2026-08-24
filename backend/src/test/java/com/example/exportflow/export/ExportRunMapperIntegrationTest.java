package com.example.exportflow.export;

import com.example.exportflow.export.domain.ExportRunTrigger;
import com.example.exportflow.export.domain.ExportStage;
import com.example.exportflow.export.domain.ExportTaskRun;
import com.example.exportflow.export.domain.ExportTaskStatus;
import com.example.exportflow.export.infrastructure.ExportRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Sql(scripts = "/export-run-mapper-schema.sql")
class ExportRunMapperIntegrationTest {

    @Autowired
    private ExportRunMapper mapper;

    @Test
    void storesOrderedRunsAndFindsIdempotentCommand() {
        LocalDateTime now = LocalDateTime.now();
        ExportTaskRun initial = run(7L, 0, ExportRunTrigger.INITIAL, "create-key", now);
        ExportTaskRun retry = run(7L, 1, ExportRunTrigger.MANUAL_RETRY, "retry-key", now.plusSeconds(1));

        assertThat(mapper.insert(initial)).isEqualTo(1);
        assertThat(mapper.insert(retry)).isEqualTo(1);

        assertThat(mapper.findByTaskId(7L)).extracting(ExportTaskRun::getRunNo).containsExactly(0, 1);
        assertThat(mapper.findByIdempotencyKey("retry-key").getId()).isEqualTo(retry.getId());
    }

    private ExportTaskRun run(long taskId, int runNo, ExportRunTrigger trigger, String key, LocalDateTime now) {
        ExportTaskRun run = new ExportTaskRun();
        run.setTaskId(taskId);
        run.setRunNo(runNo);
        run.setTriggerType(trigger);
        run.setIdempotencyKey(key);
        run.setRequestHash("a".repeat(64));
        run.setStatus(ExportTaskStatus.PENDING);
        run.setStage(ExportStage.QUEUED);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return run;
    }
}
