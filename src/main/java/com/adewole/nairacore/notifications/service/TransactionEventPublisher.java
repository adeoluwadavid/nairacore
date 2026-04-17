package com.adewole.nairacore.notifications.service;

import com.adewole.nairacore.notifications.dto.TransactionEvent;
import com.adewole.nairacore.shared.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishTransactionEvent(TransactionEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.TRANSACTION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    event
            );
            log.info("Transaction event published. Reference: {}, Type: {}",
                    event.getTransactionReference(),
                    event.getTransactionType()
            );
        } catch (Exception e) {
            log.error("Failed to publish transaction event. Reference: {}, Error: {}",
                    event.getTransactionReference(),
                    e.getMessage()
            );
        }
    }
}