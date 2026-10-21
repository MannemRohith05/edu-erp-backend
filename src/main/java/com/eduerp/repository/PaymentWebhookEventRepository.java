package com.eduerp.repository;

import com.eduerp.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderAndEventId(String provider, String eventId);
}
