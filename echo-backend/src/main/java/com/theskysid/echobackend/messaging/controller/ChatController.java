package com.theskysid.echobackend.messaging.controller;

import com.theskysid.echobackend.auth.service.OnlineUserService;
import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.theskysid.echobackend.messaging.entity.ChatMessage;
import com.theskysid.echobackend.messaging.repository.ChatMessageRepository;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import com.theskysid.echobackend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    @Autowired
    private UserService userService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipService friendshipService;

    //first the hit endpoint is checked in the config then accordingly it hits the specific methods

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public") //automatic broadcasting to all the users
    public ChatMessage addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String username = IdentifierNormalizer.normalizeUsername(chatMessage.getSender());
        String sessionId = headerAccessor.getSessionId();

        if (username.isBlank() || !userService.userExists(username)) {
            return null;
        }

        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("username", username);
        }

        boolean shouldBroadcastJoin = onlineUserService.registerSession(username, sessionId);
        if (!shouldBroadcastJoin) {
            return null;
        }

        chatMessage.setSender(username);

        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setTimestamp(LocalDateTime.now());

        if (chatMessage.getContent() == null) {
            chatMessage.setContent("");
        }

        return chatMessage;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {

        if (!userService.userExists(chatMessage.getSender())) {
            return null;
        }

        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(LocalDateTime.now());
        }

        if (chatMessage.getContent() == null) {
            chatMessage.setContent("");
        }

        // Save only real chat messages
        if (chatMessage.getType() == ChatMessage.MessageType.CHAT) {
            return chatMessageRepository.save(chatMessage);
        }

        // TYPING / JOIN / LEAVE → broadcast only
        return chatMessage;
    }

    @MessageMapping("/chat.sendPrivateMessage")
    public void sendPrivateMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String senderUsername = resolveSocketUsername(headerAccessor);
        String recipientUsername = IdentifierNormalizer.normalizeUsername(chatMessage.getRecipient());

        if (senderUsername == null || senderUsername.isBlank() || recipientUsername == null || recipientUsername.isBlank()) {
            return;
        }

        if (!userService.userExists(senderUsername) || !userService.userExists(recipientUsername)) {
            return;
        }

        User sender = userRepository.findByUsernameIgnoreCase(senderUsername)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User recipient = userRepository.findByUsernameIgnoreCase(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        if (!friendshipService.areFriends(sender, recipient)) {
            return;
        }

        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(LocalDateTime.now());
        }

        if (chatMessage.getContent() == null) {
            chatMessage.setContent("");
        }

        chatMessage.setSender(sender.getUsername());
        chatMessage.setRecipient(recipient.getUsername());
        chatMessage.setType(ChatMessage.MessageType.PRIVATE_MESSAGE);

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        String recipientDestination = "/user/" + recipient.getUsername() + "/queue/private";
        messagingTemplate.convertAndSend(recipientDestination, savedMessage);

        String senderDestination = "/user/" + sender.getUsername() + "/queue/private";
        messagingTemplate.convertAndSend(senderDestination, savedMessage);
    }

    private String resolveSocketUsername(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null && headerAccessor.getUser().getName() != null) {
            return IdentifierNormalizer.normalizeUsername(headerAccessor.getUser().getName());
        }

        if (headerAccessor.getSessionAttributes() != null) {
            Object sessionUsername = headerAccessor.getSessionAttributes().get("username");
            if (sessionUsername instanceof String username) {
                return IdentifierNormalizer.normalizeUsername(username);
            }
        }

        return null;
    }
}
