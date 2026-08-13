package com.theskysid.echobackend.messaging.controller;

import com.theskysid.echobackend.auth.service.OnlineUserService;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.theskysid.echobackend.messaging.entity.ChatMessage;
import com.theskysid.echobackend.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    @Autowired
    private UserService userService;

    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * Presence registration: a client announces itself, we track the session and
     * broadcast a JOIN to /topic/public (drives the online-user list).
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
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
}
