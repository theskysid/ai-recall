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
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern CHANNEL_TOPIC = Pattern.compile("/topic/channel/([1-9][0-9]*)");
    private static final Pattern CHANNEL_SEND = Pattern.compile("/app/channel/([1-9][0-9]*)/send");

    public static final String AUTHENTICATED_USER_ID = "chatAuthenticatedUserId";

    private record SessionUser(Long id, String username, AtomicLong lastTypingNanos) {}

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
            sessionUsers.put(headers.getSessionId(), new SessionUser(user.getId(), user.getUsername(), new AtomicLong(Long.MIN_VALUE)));
            return message;
        }

        SessionUser user = headers.getSessionId() == null ? null : sessionUsers.get(headers.getSessionId());
        Principal principal = headers.getUser();
        if (user == null || principal == null || !user.username().equals(principal.getName())) throw denied();

        String destination = headers.getDestination();
        if (command == StompCommand.SEND &&
                ("/app/dm.sendMessage".equals(destination) || "/app/dm.typing".equals(destination))) {
            // Dropping excess typing frames performs no action and needs no database trip.
            if ("/app/dm.typing".equals(destination) && !allowTyping(user)) return null;
            // The controller resolves this server-owned id and checks the current name.
            // Do not resolve by username: an old session must never impersonate a renamed account.
            headers.setHeader(AUTHENTICATED_USER_ID, user.id());
            return message;
        }
        if (headers.getMessageType() == SimpMessageType.HEARTBEAT || command == StompCommand.UNSUBSCRIBE) return message;

        if (command == StompCommand.SUBSCRIBE) {
            if (destination != null && List.of("/topic/public", "/user/queue/dm", "/user/queue/friends").contains(destination)) {
                requireCurrentAccount(user);
                return message;
            }
            requireChannelMember(user, destination, CHANNEL_TOPIC);
        } else if (command == StompCommand.SEND) {
            if ("/app/chat.addUser".equals(destination)) {
                requireCurrentAccount(user);
                return message;
            }
            // Clients cannot publish directly to broker topics or private queues.
            requireChannelMember(user, destination, CHANNEL_SEND);
        } else {
            throw denied();
        }
        return message;
    }

    /** Recheck current account identity and membership without fetching full entities. */
    public Message<?> authorizeDelivery(Message<?> message) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        SessionUser user = sessionId == null ? null : sessionUsers.get(sessionId);
        if (user == null) return null;
        try {
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (destination != null && CHANNEL_TOPIC.matcher(destination).matches()) {
                requireChannelMember(user, destination, CHANNEL_TOPIC);
            } else {
                requireCurrentAccount(user);
            }
            return message;
        } catch (RuntimeException e) {
            return null; // Fail closed if current access cannot be established.
        }
    }

    private boolean allowTyping(SessionUser user) {
        long now = System.nanoTime();
        for (;;) {
            long previous = user.lastTypingNanos().get();
            if (previous != Long.MIN_VALUE && now - previous < 1_000_000_000L) return false;
            if (user.lastTypingNanos().compareAndSet(previous, now)) return true;
        }
    }

    private void requireCurrentAccount(SessionUser user) {
        if (!userRepository.existsByIdAndUsername(user.id(), user.username())) throw denied();
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

    private void requireChannelMember(SessionUser user, String destination, Pattern pattern) {
        var match = pattern.matcher(destination == null ? "" : destination);
        if (!match.matches()) throw denied();
        try {
            channelService.requireMember(user.id(), user.username(), Long.valueOf(match.group(1)));
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
