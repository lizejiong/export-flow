package com.example.exportflow.export.infrastructure.messaging;

import com.example.exportflow.export.application.ExportExecutionService;
import com.example.exportflow.export.application.TaskClaimService;
import com.example.exportflow.export.domain.ExportTask;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
