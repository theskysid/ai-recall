package com.theskysid.echobackend.config;

import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SockJS/STOMP connections exercise authentication and user queue routing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = WebSocketSecurityTest.TestApp.class,
        properties = {"spring.config.name=websocket-test", "jwt.secret=test-signing-secret-at-least-32-bytes-long", "jwt.expiration=60000", "logging.level.root=ERROR", "management.endpoints.enabled-by-default=false"})
@ActiveProfiles("websocket-regression")
class WebSocketSecurityTest {
    @Configuration
    @Profile("websocket-regression")
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
    @Import({WebSocketConfig.class, WebSocketAuthorizationInterceptor.class})
    static class TestApp {
        @Bean JwtService jwtService() {
            return new JwtService();
        }
    }

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired SimpMessagingTemplate messaging;
    @MockBean UserRepository users;
    @MockBean ChannelService channels;
    private WebSocketStompClient client;
    private final List<StompSession> sessions = new ArrayList<>();
    private User alice;
    private User bob;

    @BeforeEach
    void setup() {
        alice = User.builder().id(1L).username("alice").build();
        bob = User.builder().id(2L).username("bob").build();
        when(users.findById(1L)).thenReturn(Optional.of(alice));
        when(users.findById(2L)).thenReturn(Optional.of(bob));
        var sockJs = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
        client = new WebSocketStompClient(sockJs);
        client.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void cleanup() {
        for (var session : sessions) if (session.isConnected()) session.disconnect();
        client.stop();
        sessions.clear();
    }

    @Test
    void anonymousConnectIsRejected() {
        assertThrows(Exception.class, () -> connect(null, new LinkedBlockingQueue<>()));
    }

    @Test
    void privateMessagesAndFriendEventsReachOnlyTheirRecipient() throws Exception {
        var aliceSession = connect(alice, new LinkedBlockingQueue<>());
        var bobSession = connect(bob, new LinkedBlockingQueue<>());
        var aliceDms = new LinkedBlockingQueue<String>();
        var bobDms = new LinkedBlockingQueue<String>();
        var aliceFriends = new LinkedBlockingQueue<String>();
        aliceSession.subscribe("/user/queue/dm", handler(aliceDms));
        aliceSession.subscribe("/user/queue/friends", handler(aliceFriends));
        bobSession.subscribe("/user/queue/dm", handler(bobDms));
        // Repeat until the asynchronously processed subscription is established.
        String received = null;
        for (int i = 0; i < 20 && received == null; i++) {
            messaging.convertAndSendToUser("alice", "/queue/dm", "private");
            received = aliceDms.poll(100, TimeUnit.MILLISECONDS);
        }
        assertEquals("private", received);
        assertNull(bobDms.poll(200, TimeUnit.MILLISECONDS));
        messaging.convertAndSendToUser("alice", "/queue/friends", "friend-event");
        assertEquals("friend-event", aliceFriends.poll(3, TimeUnit.SECONDS));
    }

    @Test
    void subscribingToAnotherUsersQueueIsRejected() throws Exception {
        var errors = new LinkedBlockingQueue<String>();
        var session = connect(bob, errors);
        session.subscribe("/user/alice/queue/dm", handler(new LinkedBlockingQueue<>()));
        assertNotNull(errors.poll(3, TimeUnit.SECONDS));
    }

    @Test
    void channelSubscriptionsStopDeliveringAfterMembershipIsRevoked() throws Exception {
        var session = connect(alice, new LinkedBlockingQueue<>());
        var received = new LinkedBlockingQueue<String>();
        session.subscribe("/topic/channel/42", handler(received));
        String first = null;
        for (int i = 0; i < 20 && first == null; i++) {
            messaging.convertAndSend("/topic/channel/42", "member-message");
            first = received.poll(100, TimeUnit.MILLISECONDS);
        }
        assertEquals("member-message", first);
        received.clear();
        doThrow(new IllegalStateException("left channel")).when(channels).requireMember(alice, 42L);
        messaging.convertAndSend("/topic/channel/42", "after-leave");
        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void nonMemberChannelSubscriptionsAreRejected() throws Exception {
        doThrow(new IllegalStateException("not a member")).when(channels).requireMember(bob, 42L);
        var errors = new LinkedBlockingQueue<String>();
        var session = connect(bob, errors);
        session.subscribe("/topic/channel/42", handler(new LinkedBlockingQueue<>()));
        assertNotNull(errors.poll(3, TimeUnit.SECONDS));
    }

    private StompSession connect(User user, BlockingQueue<String> errors) throws Exception {
        var headers = new StompHeaders();
        if (user != null) headers.set("Authorization", "Bearer " + jwt.generateToken(user));
        var session = client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                    @Override public void handleFrame(StompHeaders headers, Object payload) { errors.add(String.valueOf(payload)); }
                    @Override public void handleTransportError(StompSession session, Throwable exception) { errors.add(exception.toString()); }
                }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private StompFrameHandler handler(BlockingQueue<String> received) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { received.add((String) payload); }
        };
    }
}
