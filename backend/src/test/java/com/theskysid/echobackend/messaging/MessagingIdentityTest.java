package com.theskysid.echobackend.messaging;

import com.theskysid.echobackend.auth.service.OnlineUserService;
import com.theskysid.echobackend.messaging.controller.ChatController;
import com.theskysid.echobackend.messaging.dto.DirectMessageRequestDTO;
import com.theskysid.echobackend.messaging.entity.ChatMessage;
import com.theskysid.echobackend.messaging.service.DirectMessageService;
import com.theskysid.echobackend.messaging.websocket.DirectMessageWsController;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagingIdentityTest {
    @Mock UserRepository users;
    @Mock OnlineUserService presence;
    @Mock DirectMessageService directMessages;
    @Mock SimpMessagingTemplate messaging;
    @InjectMocks ChatController chat;
    @InjectMocks DirectMessageWsController dm;

    @Test
    void presenceIgnoresClaimedSenderAndUsesPrincipal() {
        var alice = User.builder().id(1L).username("alice").build();
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        when(presence.registerSession("alice", "session")).thenReturn(true);
        var headers = SimpMessageHeaderAccessor.create();
        headers.setUser(() -> "alice");
        headers.setSessionId("session");
        headers.setSessionAttributes(new HashMap<>());
        var payload = new ChatMessage();
        payload.setSender("victim");
        assertEquals("alice", chat.addUser(payload, headers).getSender());
        verify(presence).registerSession("alice", "session");
    }

    @Test
    void anonymousPresenceCannotRegisterAnotherUser() {
        var payload = new ChatMessage();
        payload.setSender("victim");
        assertNull(chat.addUser(payload, SimpMessageHeaderAccessor.create()));
        verifyNoInteractions(users, presence);
    }

    @Test
    void legacySessionUsernameCannotAuthorizeMessagesOrTyping() {
        var headers = SimpMessageHeaderAccessor.create();
        headers.setSessionAttributes(new HashMap<>(java.util.Map.of("username", "victim")));
        var request = new DirectMessageRequestDTO();
        request.setSenderUsername("victim");
        dm.sendDirectMessage(request, headers);
        dm.handleTyping(request, headers);
        verifyNoInteractions(users, directMessages, messaging);
    }
}
