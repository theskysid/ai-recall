package com.theskysid.echobackend.channel.websocket;

import com.theskysid.echobackend.channel.dto.ChannelMessageDTO;
import com.theskysid.echobackend.channel.dto.ChannelMessageRequestDTO;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import com.theskysid.echobackend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChannelChatController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Clients send to /app/channel/{channelId}/send and subscribe to /topic/channel/{channelId}

    @MessageMapping("/channel/{channelId}/send")
    public void sendChannelMessage(@DestinationVariable Long channelId,
                                   @Payload ChannelMessageRequestDTO request) {
        String senderUsername = IdentifierNormalizer.normalizeUsername(request.getSender());
        if (senderUsername == null || senderUsername.isBlank() || !userService.userExists(senderUsername)) {
            return;
        }

        ChannelMessage.MessageType type = parseType(request.getType());

        // Only real CHAT messages are persisted; TYPING/JOIN/LEAVE are broadcast only.
        if (type == ChannelMessage.MessageType.CHAT) {
            try {
                User sender = userRepository.findByUsernameIgnoreCase(senderUsername)
                        .orElseThrow(() -> new RuntimeException("Sender not found"));
                ChannelMessage saved = channelService.postMessage(
                        channelId, sender, request.getContent(), request.getColor());
                broadcast(channelId, toDTO(saved));
            } catch (RuntimeException e) {
                // Sender not a member / channel missing — drop silently, matching global chat behaviour.
            }
            return;
        }

        broadcast(channelId, ChannelMessageDTO.builder()
                .channelId(channelId)
                .sender(senderUsername)
                .content(request.getContent() == null ? "" : request.getContent())
                .color(request.getColor())
                .type(type.name())
                .timestamp(LocalDateTime.now())
                .build());
    }

    private void broadcast(Long channelId, ChannelMessageDTO dto) {
        messagingTemplate.convertAndSend("/topic/channel/" + channelId, dto);
    }

    private ChannelMessage.MessageType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return ChannelMessage.MessageType.CHAT;
        }
        try {
            return ChannelMessage.MessageType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChannelMessage.MessageType.CHAT;
        }
    }

    private ChannelMessageDTO toDTO(ChannelMessage message) {
        return ChannelMessageDTO.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .sender(message.getSender().getUsername())
                .content(message.getContent())
                .color(message.getColor())
                .type(message.getType().name())
                .timestamp(message.getTimestamp())
                .build();
    }
}
