package com.theskysid.echobackend.messaging.websocket;

import com.theskysid.echobackend.config.ChatLatencyInterceptor;
import com.theskysid.echobackend.config.WebSocketAuthorizationInterceptor;
import com.theskysid.echobackend.messaging.dto.DirectMessageDTO;
import com.theskysid.echobackend.messaging.dto.DirectMessageRequestDTO;
import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.messaging.entity.DirectMessage;
import com.theskysid.echobackend.messaging.service.DirectMessageService;
import com.theskysid.echobackend.user.entity.User;
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
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/dm.sendMessage")
    public void sendDirectMessage(@Payload DirectMessageRequestDTO request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long senderId = authenticatedSenderId(headerAccessor);
            if (senderId == null) return;
            DirectMessage saved = directMessageService.sendMessage(request.getConversationId(), senderId,
                    headerAccessor.getUser().getName(), request.getRecipientUsername(), request.getContent());
            User sender = saved.getSender();
            Conversation conversation = saved.getConversation();
            DirectMessageDTO dto = toDTO(saved);
            dto.setClientMessageId(request.getClientMessageId());

            User recipient = conversation.getOtherParticipant(sender);

            messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/dm", dto, ChatLatencyInterceptor.deliveryHeaders(headerAccessor));
            messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/dm", dto, ChatLatencyInterceptor.deliveryHeaders(headerAccessor));

            logger.info("Direct message sent from {} to {} in conversation {}", sender.getUsername(), recipient.getUsername(), conversation.getId());
        } catch (Exception e) {
            logger.error("Error sending DM: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/dm.typing")
    @Transactional
    public void handleTyping(@Payload DirectMessageRequestDTO request, SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long senderId = authenticatedSenderId(headerAccessor);
            if (senderId == null) return;
            var access = directMessageService.getSocketConversation(request.getConversationId(), senderId,
                    headerAccessor.getUser().getName(), request.getRecipientUsername());
            User sender = access.sender();
            Conversation conversation = access.conversation();

            User recipient = conversation.getOtherParticipant(sender);

            DirectMessageDTO typingDto = DirectMessageDTO.builder()
                    .conversationId(conversation.getId())
                    .senderId(sender.getId())
                    .senderUsername(sender.getUsername())
                    .recipientUsername(recipient.getUsername())
                    .content("TYPING")
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/dm", typingDto, ChatLatencyInterceptor.deliveryHeaders(headerAccessor));
        } catch (Exception e) {
            logger.error("Error handling DM typing indicator: {}", e.getMessage());
        }
    }

    private Long authenticatedSenderId(SimpMessageHeaderAccessor headers) {
        if (headers.getUser() == null || !(headers.getHeader(WebSocketAuthorizationInterceptor.AUTHENTICATED_USER_ID) instanceof Long id)) return null;
        return id;
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
