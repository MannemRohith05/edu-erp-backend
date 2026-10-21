package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_provider_event", columnNames = {"provider", "event_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentWebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "event_id", nullable = false, length = 160) private String eventId;
    @Column(nullable = false) private LocalDateTime receivedAt;
    @PrePersist void onCreate() { if (receivedAt == null) receivedAt = LocalDateTime.now(); }
}
