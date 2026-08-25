package com.example.exportflow.export.infrastructure.messaging;

import com.example.exportflow.common.api.RequestIdContext;
import com.example.exportflow.export.application.ExportExecutionService;
import com.example.exportflow.export.application.TaskClaimService;
import com.example.exportflow.export.domain.ExportTask;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class ExportTaskListener {
    private static final Logger log = LoggerFactory.getLogger(ExportTaskListener.class);
    private final TaskClaimService claimService;
    private final ExportExecutionService executionService;

    public ExportTaskListener(TaskClaimService claimService, ExportExecutionService executionService) {
        this.claimService = claimService;
        this.executionService = executionService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.TASK_QUEUE)
    public void consume(ExportTaskMessage payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        if (payload == null || !payload.isSupported()) {
            log.error("Rejecting unsupported export message, payload={}", payload);
            channel.basicReject(deliveryTag, false);
            return;
        }
        String requestId = payload.requestId() == null || payload.requestId().isBlank()
                ? RequestIdContext.currentOrCreate() : payload.requestId();
        try (MDC.MDCCloseable requestScope = MDC.putCloseable(RequestIdContext.MDC_KEY, requestId);
             MDC.MDCCloseable taskScope = MDC.putCloseable("taskId", Long.toString(payload.taskId()))) {
            try {
                Optional<ExportTask> claimed = claimService.claim(payload.taskId());
                if (claimed.isPresent()) executionService.execute(claimed.get());
                channel.basicAck(deliveryTag, false);
            } catch (Exception exception) {
                log.error("Export message handling failed, taskId={}", payload.taskId(), exception);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
