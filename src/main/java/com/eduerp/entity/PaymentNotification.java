package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_notifications", uniqueConstraints = @UniqueConstraint(name = "uk_payment_notification_delivery", columnNames = {"payment_id", "channel", "recipient"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id")
    private FeePayment payment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    @Column(nullable = false, length = 255)
    private String recipient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private NotificationStatus status = NotificationStatus.PENDING;
    @Column(nullable = false)
    @Builder.Default private Integer attempts = 0;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
