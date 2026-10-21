package com.eduerp.service;

import com.eduerp.entity.AuditEvent;
import com.eduerp.entity.User;
import com.eduerp.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository repository;

    @Transactional
    public void record(User actor, String action, String entityType, Object entityId, String details) {
        repository.save(AuditEvent.builder().actor(actor).action(action).entityType(entityType)
                .entityId(String.valueOf(entityId)).details(details).build());
    }
}
