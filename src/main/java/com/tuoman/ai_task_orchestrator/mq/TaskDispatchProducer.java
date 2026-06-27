package com.tuoman.ai_task_orchestrator.mq;

import com.tuoman.ai_task_orchestrator.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskDispatchProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendTaskCreatedMessage(Long taskId) {
        TaskDispatchMessage message = new TaskDispatchMessage(taskId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TASK_EXCHANGE,
                RabbitMQConfig.TASK_CREATED_ROUTING_KEY,
                message
        );
    }
}