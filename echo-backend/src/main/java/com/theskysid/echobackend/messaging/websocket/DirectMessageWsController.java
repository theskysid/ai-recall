package com.theskysid.echobackend.messaging.websocket;

import com.theskysid.echobackend.messaging.dto.DirectMessageDTO;
import com.theskysid.echobackend.messaging.dto.DirectMessageRequestDTO;
import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.messaging.entity.DirectMessage;
import com.theskysid.echobackend.messaging.service.DirectMessageService;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Controller
public class DirectMessageWsController {

    private static final Logger logger = LoggerFactory.getLogger(DirectMessageWsController.class);

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/dm.sendMessage")
    @Transactional
    public void sendDirectMessage(@Payload DirectMessageRequestDTO request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String senderUsername = resolveSenderUsername(request, headerAccessor);
            if (senderUsername == null || senderUsername.isBlank()) {
                logger.warn("DM send failed: sender username could not be resolved");
                return;
            }

            User sender = userRepository.findByUsernameIgnoreCase(senderUsername)
                    .orElseThrow(() -> new RuntimeException("Sender not found"));

            Conversation conversation;
            if (request.getConversationId() != null) {
                conversation = directMessageService.getConversation(request.getConversationId(), sender);
            } else if (request.getRecipientUsername() != null && !request.getRecipientUsername().isBlank()) {
                User recipient = userRepository.findByUsernameIgnoreCase(request.getRecipientUsername())
                        .orElseThrow(() -> new RuntimeException("Recipient not found"));
                conversation = directMessageService.getOrCreateConversation(sender, recipient);
            } else {
                logger.warn("DM send failed: missing conversationId and recipientUsername");
                return;
            }

            DirectMessage saved = directMessageService.sendMessage(conversation, sender, request.getContent());
            DirectMessageDTO dto = toDTO(saved);

            User recipient = conversation.getOtherParticipant(sender);

            String recipientDestination = "/user/" + recipient.getUsername() + "/queue/dm";
            messagingTemplate.convertAndSend(recipientDestination, dto);

            String senderDestination = "/user/" + sender.getUsername() + "/queue/dm";
            messagingTemplate.convertAndSend(senderDestination, dto);

            logger.info("Direct message sent from {} to {} in conversation {}", sender.getUsername(), recipient.getUsername(), conversation.getId());
        } catch (Exception e) {
            logger.error("Error sending DM: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/dm.typing")
    @Transactional
    public void handleTyping(@Payload DirectMessageRequestDTO request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String senderUsername = resolveSenderUsername(request, headerAccessor);
            if (senderUsername == null || senderUsername.isBlank()) {
                return;
            }

            User sender = userRepository.findByUsernameIgnoreCase(senderUsername)
                    .orElseThrow(() -> new RuntimeException("Sender not found"));

            Conversation conversation;
            if (request.getConversationId() != null) {
                conversation = directMessageService.getConversation(request.getConversationId(), sender);
            } else if (request.getRecipientUsername() != null && !request.getRecipientUsername().isBlank()) {
                User recipient = userRepository.findByUsernameIgnoreCase(request.getRecipientUsername())
                        .orElseThrow(() -> new RuntimeException("Recipient not found"));
                conversation = directMessageService.getOrCreateConversation(sender, recipient);
            } else {
                return;
            }

            User recipient = conversation.getOtherParticipant(sender);

            DirectMessageDTO typingDto = DirectMessageDTO.builder()
                    .conversationId(conversation.getId())
                    .senderId(sender.getId())
                    .senderUsername(sender.getUsername())
                    .recipientUsername(recipient.getUsername())
                    .content("TYPING")
                    .timestamp(LocalDateTime.now())
                    .build();

            String recipientDestination = "/user/" + recipient.getUsername() + "/queue/dm";
            messagingTemplate.convertAndSend(recipientDestination, typingDto);
        } catch (Exception e) {
            logger.error("Error handling DM typing indicator: {}", e.getMessage());
        }
    }

    private String resolveSenderUsername(DirectMessageRequestDTO request, SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null && headerAccessor.getUser().getName() != null) {
            return headerAccessor.getUser().getName();
        }
        if (headerAccessor.getSessionAttributes() != null && headerAccessor.getSessionAttributes().get("username") != null) {
            return (String) headerAccessor.getSessionAttributes().get("username");
        }
        return request.getSenderUsername();
    }

    private DirectMessageDTO toDTO(DirectMessage message) {
        User recipient = message.getConversation().getOtherParticipant(message.getSender());
        return DirectMessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .recipientUsername(recipient.getUsername())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .expiresAt(message.getExpiresAt())
                .build();
    }
}
