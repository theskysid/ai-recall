package com.theskysid.echobackend.config;

import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern CHANNEL_TOPIC = Pattern.compile("/topic/channel/([1-9][0-9]*)");
    private static final Pattern CHANNEL_SEND = Pattern.compile("/app/channel/([1-9][0-9]*)/send");

    private record SessionUser(Long id, String username) {}

    private final Map<String, SessionUser> sessionUsers = new ConcurrentHashMap<>();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelService channelService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) {
            throw denied();
        }
        StompCommand command = headers.getCommand();
        if (command == StompCommand.DISCONNECT) {
            forgetSession(headers.getSessionId());
            return message;
        }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            User user = authenticate(headers);
            if (headers.getSessionId() == null) {
                throw denied();
            }
            headers.setUser(new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
            sessionUsers.put(headers.getSessionId(), new SessionUser(user.getId(), user.getUsername()));
            return message;
        }

        User user = sessionUser(headers.getSessionId());
        Principal principal = headers.getUser();
        if (user == null || principal == null || !user.getUsername().equals(principal.getName())) {
            throw denied();
        }
        if (headers.getMessageType() == SimpMessageType.HEARTBEAT || command == StompCommand.UNSUBSCRIBE) {
            return message;
        }

        String destination = headers.getDestination();
        if (command == StompCommand.SUBSCRIBE) {
            if (destination != null && List.of("/topic/public", "/user/queue/dm", "/user/queue/friends").contains(destination)) {
                return message;
            }
            requireChannelMember(user, destination, CHANNEL_TOPIC);
        } else if (command == StompCommand.SEND) {
            if (destination != null && List.of("/app/chat.addUser", "/app/dm.sendMessage", "/app/dm.typing").contains(destination)) {
                return message;
            }
            // Clients cannot publish directly to broker topics or private queues.
            requireChannelMember(user, destination, CHANNEL_SEND);
        } else {
            throw denied();
        }
        return message;
    }

    /** Recheck existing subscriptions after membership changes, including leave. */
    public Message<?> authorizeDelivery(Message<?> message) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        try {
            User user = sessionUser(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
            if (user == null) {
                return null;
            }
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (destination != null && CHANNEL_TOPIC.matcher(destination).matches()) {
                requireChannelMember(user, destination, CHANNEL_TOPIC);
            }
            return message;
        } catch (RuntimeException e) {
            // Fail closed if membership cannot be established.
            return null;
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forgetSession(event.getSessionId());
    }

    private User authenticate(StompHeaderAccessor headers) {
        String authorization = headers.getFirstNativeHeader("Authorization");
        if (authorization != null) {
            try {
                if (!authorization.startsWith("Bearer ")) {
                    throw denied();
                }
                String token = authorization.substring(7);
                Long id = jwtService.extractUserId(token);
                User user = id == null ? null : userRepository.findById(id).orElse(null);
                if (user != null && jwtService.isTokenValid(token, user)) {
                    return user;
                }
            } catch (RuntimeException e) {
                throw denied();
            }
            throw denied();
        }
        // Cookie-authenticated SockJS handshakes already have a server principal.
        Principal principal = headers.getUser();
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw denied();
        }
        return userRepository.findByUsernameIgnoreCase(principal.getName()).orElseThrow(this::denied);
    }

    private User sessionUser(String sessionId) {
        SessionUser sessionUser = sessionId == null ? null : sessionUsers.get(sessionId);
        if (sessionUser == null) {
            return null;
        }
        // A renamed account must reconnect before its old user queue can deliver.
        return userRepository.findById(sessionUser.id())
                .filter(user -> user.getUsername().equals(sessionUser.username())).orElse(null);
    }

    private void requireChannelMember(User user, String destination, Pattern pattern) {
        var match = pattern.matcher(destination == null ? "" : destination);
        if (!match.matches()) {
            throw denied();
        }
        try {
            channelService.requireMember(user, Long.valueOf(match.group(1)));
        } catch (RuntimeException e) {
            throw denied();
        }
    }

    private void forgetSession(String sessionId) {
        if (sessionId != null) {
            sessionUsers.remove(sessionId);
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("WebSocket access denied");
    }
}
