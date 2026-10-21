package com.eduerp.repository;

import com.eduerp.entity.NotificationChannel;
import com.eduerp.entity.NotificationStatus;
import com.eduerp.entity.PaymentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {
    Optional<PaymentNotification> findByPaymentIdAndChannelAndRecipient(Long paymentId, NotificationChannel channel, String recipient);
    List<PaymentNotification> findByPaymentId(Long paymentId);
    List<PaymentNotification> findByStatus(NotificationStatus status);
}
