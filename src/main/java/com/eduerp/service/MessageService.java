package com.eduerp.service;

import com.eduerp.dto.MessageDTO;
import com.eduerp.entity.Message;
import com.eduerp.entity.User;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.MessageRepository;
import com.eduerp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public List<MessageDTO> getInboxMessages(Long userId) {
        return messageRepository.findByReceiverIdOrderBySentAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getSentMessages(Long userId) {
        return messageRepository.findBySenderIdOrderBySentAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getUnreadMessages(Long userId) {
        return messageRepository.findByReceiverIdAndIsReadFalse(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public Long getUnreadCount(Long userId) {
        return messageRepository.countUnreadByReceiverId(userId);
    }

    public MessageDTO getMessageById(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
        return mapToDTO(message);
    }

    @Transactional
    public MessageDTO sendMessage(MessageDTO dto, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));

        User receiver = userRepository.findById(dto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found with id: " + dto.getReceiverId()));

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .subject(dto.getSubject())
                .content(dto.getContent())
                .isRead(false)
                .build();

        Message savedMessage = messageRepository.save(message);
        return mapToDTO(savedMessage);
    }

    @Transactional
    public MessageDTO markAsRead(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));

        message.markAsRead();
        Message updatedMessage = messageRepository.save(message);
        return mapToDTO(updatedMessage);
    }

    @Transactional
    public void deleteMessage(Long id) {
        if (!messageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Message not found with id: " + id);
        }
        messageRepository.deleteById(id);
    }

    private MessageDTO mapToDTO(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .receiverId(message.getReceiver().getId())
                .receiverName(message.getReceiver().getFullName())
                .subject(message.getSubject())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .isRead(message.getIsRead())
                .build();
    }
}
