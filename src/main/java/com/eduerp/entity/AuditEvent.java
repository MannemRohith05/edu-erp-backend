package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "entity_type", nullable = false, length = 80) private String entityType;
    @Column(name = "entity_id", nullable = false, length = 80) private String entityId;
    @Column(length = 1000) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
