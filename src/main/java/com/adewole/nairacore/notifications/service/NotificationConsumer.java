package com.adewole.nairacore.notifications.service;

import com.adewole.nairacore.notifications.dto.TransactionEvent;
import com.adewole.nairacore.notifications.entity.NotificationChannel;
import com.adewole.nairacore.notifications.entity.NotificationLog;
import com.adewole.nairacore.notifications.entity.NotificationStatus;
import com.adewole.nairacore.notifications.entity.NotificationType;
import com.adewole.nairacore.notifications.repository.NotificationLogRepository;
import com.adewole.nairacore.shared.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationLogRepository notificationLogRepository;
    private final JavaMailSender mailSender;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consumeTransactionEvent(TransactionEvent event) {
        log.info("Notification event received. Reference: {}, Type: {}",
                event.getTransactionReference(),
                event.getTransactionType()
        );

        String subject = buildSubject(event);
        String message = buildMessage(event);
        NotificationType type = mapToNotificationType(event);

        try {
            // Send actual email via Mailpit
            sendEmail(event.getRecipientEmail(), subject, message);

            NotificationLog notificationLog = NotificationLog.builder()
                    .userId(event.getUserId())
                    .accountNumber(event.getAccountNumber())
                    .transactionReference(event.getTransactionReference())
                    .type(type)
                    .channel(NotificationChannel.EMAIL)
                    .recipient(event.getRecipientEmail())
                    .subject(subject)
                    .message(message)
                    .status(NotificationStatus.SENT)
                    .build();

            notificationLogRepository.save(notificationLog);

            log.info("Email sent successfully to: {} | Reference: {}",
                    event.getRecipientEmail(),
                    event.getTransactionReference()
            );

        } catch (Exception e) {
            log.error("Failed to send notification. Reference: {}, Error: {}",
                    event.getTransactionReference(),
                    e.getMessage()
            );

            NotificationLog failedLog = NotificationLog.builder()
                    .userId(event.getUserId())
                    .accountNumber(event.getAccountNumber())
                    .transactionReference(event.getTransactionReference())
                    .type(type)
                    .channel(NotificationChannel.EMAIL)
                    .recipient(event.getRecipientEmail())
                    .subject(subject)
                    .message(message)
                    .status(NotificationStatus.FAILED)
                    .failureReason(e.getMessage())
                    .build();

            notificationLogRepository.save(failedLog);
        }
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private void sendEmail(String to, String subject, String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom("noreply@nairacore.com");
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);
        mailSender.send(mailMessage);
    }

    private String buildSubject(TransactionEvent event) {
        return switch (event.getTransactionType()) {
            case DEPOSIT -> "Deposit Successful — " + event.getTransactionReference();
            case WITHDRAWAL -> "Withdrawal Successful — " + event.getTransactionReference();
            case TRANSFER -> "Transfer Successful — " + event.getTransactionReference();
        };
    }

    private String buildMessage(TransactionEvent event) {
        String formattedAmount = event.getCurrency() + " " +
                String.format("%,.2f", event.getAmount());
        String formattedBalance = event.getCurrency() + " " +
                String.format("%,.2f", event.getBalanceAfter());

        return switch (event.getTransactionType()) {
            case DEPOSIT -> String.format(
                    "Dear %s,\n\n" +
                            "Your account %s has been credited with %s.\n" +
                            "Available balance: %s.\n" +
                            "Transaction reference: %s.\n\n" +
                            "If you did not initiate this transaction, please contact us immediately.\n\n" +
                            "NairaCore Banking",
                    event.getRecipientName(),
                    event.getAccountNumber(),
                    formattedAmount,
                    formattedBalance,
                    event.getTransactionReference()
            );
            case WITHDRAWAL -> String.format(
                    "Dear %s,\n\n" +
                            "A withdrawal of %s has been made from your account %s.\n" +
                            "Available balance: %s.\n" +
                            "Transaction reference: %s.\n\n" +
                            "If you did not initiate this transaction, please contact us immediately.\n\n" +
                            "NairaCore Banking",
                    event.getRecipientName(),
                    formattedAmount,
                    event.getAccountNumber(),
                    formattedBalance,
                    event.getTransactionReference()
            );
            case TRANSFER -> String.format(
                    "Dear %s,\n\n" +
                            "A transfer of %s has been made from your account %s.\n" +
                            "Available balance: %s.\n" +
                            "Transaction reference: %s.\n\n" +
                            "If you did not initiate this transaction, please contact us immediately.\n\n" +
                            "NairaCore Banking",
                    event.getRecipientName(),
                    formattedAmount,
                    event.getAccountNumber(),
                    formattedBalance,
                    event.getTransactionReference()
            );
        };
    }

    private NotificationType mapToNotificationType(TransactionEvent event) {
        return switch (event.getTransactionType()) {
            case DEPOSIT -> NotificationType.DEPOSIT_SUCCESS;
            case WITHDRAWAL -> NotificationType.WITHDRAWAL_SUCCESS;
            case TRANSFER -> NotificationType.TRANSFER_DEBIT;
        };
    }
}