package com.eduerp.repository;

import com.eduerp.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByReceiverIdOrderBySentAtDesc(Long receiverId);

    List<Message> findBySenderIdOrderBySentAtDesc(Long senderId);

    List<Message> findByReceiverIdAndIsReadFalse(Long receiverId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver.id = :receiverId AND m.isRead = false")
    Long countUnreadByReceiverId(@Param("receiverId") Long receiverId);

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :userId OR m.receiver.id = :userId) ORDER BY m.sentAt DESC")
    List<Message> findAllByUserId(@Param("userId") Long userId);
}
