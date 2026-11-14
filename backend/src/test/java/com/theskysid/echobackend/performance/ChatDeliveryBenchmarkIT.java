package com.theskysid.echobackend.performance;

import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.config.*;
import com.theskysid.echobackend.friendship.entity.*;
import com.theskysid.echobackend.friendship.repository.FriendshipRepository;
import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.messaging.dto.DirectMessageDTO;
import com.theskysid.echobackend.messaging.entity.*;
import com.theskysid.echobackend.messaging.repository.ConversationRepository;
import com.theskysid.echobackend.messaging.service.DirectMessageService;
import com.theskysid.echobackend.messaging.websocket.DirectMessageWsController;
import com.theskysid.echobackend.user.entity.*;
import com.theskysid.echobackend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Same-process clocks measure actual sender-to-recipient delivery through PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ChatDeliveryBenchmarkIT.Config.class, properties = {
        "spring.config.name=chat-benchmark", "jwt.secret=test-signing-secret-at-least-32-bytes-long", "jwt.expiration=60000",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.root=ERROR", "management.endpoints.enabled-by-default=false"})
@EnabledIfEnvironmentVariable(named = "RECALL_CHAT_TEST_DB_URL", matches = ".+")
@ActiveProfiles("chat-benchmark")
class ChatDeliveryBenchmarkIT {
    @Configuration
    @Profile("chat-benchmark")
    @EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
    @EntityScan(basePackageClasses = {User.class, Conversation.class, Friendship.class})
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, ConversationRepository.class, FriendshipRepository.class})
    @Import({WebSocketConfig.class, WebSocketAuthorizationInterceptor.class, ChatLatencyInterceptor.class, DirectMessageWsController.class,
            DirectMessageService.class, FriendshipService.class})
    static class Config {
        @Bean JwtService jwtService() { return new JwtService(); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("RECALL_CHAT_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/recall_chat_benchmark(\\?.*)?"))
            throw new IllegalArgumentException("Use a disposable local recall_chat_benchmark database");
        properties.add("spring.datasource.url", () -> url);
    }

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired ConversationRepository conversations;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired EntityManagerFactory entities;
    @Autowired MeterRegistry metrics;
    @MockBean ChannelService channels;

    @Test
    void measureTwoClientDeliveryAndQueryCount() throws Exception {
        User alice = users.save(User.builder().username("benchmark-alice").authProvider(AuthProvider.EMAIL).build());
        User bob = users.save(User.builder().username("benchmark-bob").authProvider(AuthProvider.EMAIL).build());
        friendships.save(Friendship.builder().requester(alice).addressee(bob).status(FriendshipStatus.ACCEPTED).build());
        Conversation conversation = conversations.save(Conversation.builder().participantOne(alice).participantTwo(bob).build());
        var client = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompSession sender = null, recipient = null;
        try {
            sender = connect(client, alice);
            recipient = connect(client, bob);
            var received = new LinkedBlockingQueue<Map<String, Object>>();
            var senderEchoes = new LinkedBlockingQueue<Map<String, Object>>();
            recipient.subscribe("/user/queue/dm", handler(received));
            sender.subscribe("/user/queue/dm", handler(senderEchoes));
            // Establish subscriptions without relying on sleeps or broker receipt support.
            for (int i = 0; i < 30; i++) {
                messaging.convertAndSendToUser(bob.getUsername(), "/queue/dm", DirectMessageDTO.builder().content("ready").build());
                if (received.poll(100, TimeUnit.MILLISECONDS) != null) break;
                if (i == 29) fail("Recipient subscription did not become ready");
            }
            for (int i = 0; i < 5; i++) sendAndAwait(sender, conversation.getId(), received, "warmup-" + i);
            var statistics = entities.unwrap(SessionFactory.class).getStatistics();
            // Wait for the sender echo as well before clearing global SQL statistics.
            while (senderEchoes.poll(100, TimeUnit.MILLISECONDS) != null) { }
            statistics.clear();
            List<Double> elapsed = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                long start = System.nanoTime();
                sendAndAwait(sender, conversation.getId(), received, "measured-" + i);
                assertNotNull(senderEchoes.poll(5, TimeUnit.SECONDS));
                elapsed.add((System.nanoTime() - start) / 1_000_000.0);
            }
            Collections.sort(elapsed);
            assertTrue(statistics.getPrepareStatementCount() <= 5L * elapsed.size(), "DM delivery must not reintroduce redundant SQL");
            assertNotNull(metrics.find("chat.server.handle").tag("event", "dm.send").timer());
            assertTrue(metrics.get("chat.server.handle").tag("event", "dm.send").timer().count() >= 30);
            assertNotNull(metrics.find("chat.server.delivery").tag("event", "dm.send").timer());
            System.out.printf(Locale.ROOT, "CHAT_BENCHMARK samples=%d median_ms=%.3f p95_ms=%.3f sql_per_message=%.2f%n",
                    elapsed.size(), elapsed.get(elapsed.size() / 2), elapsed.get((int) Math.ceil(elapsed.size() * .95) - 1),
                    statistics.getPrepareStatementCount() / (double) elapsed.size());
        } finally {
            if (sender != null && sender.isConnected()) sender.disconnect();
            if (recipient != null && recipient.isConnected()) recipient.disconnect();
            client.stop();
        }
    }

    @Test
    void combinedQueryRejectsOutsidersRenamedIdentitiesAndRevokedFriendships() {
        User alice = users.save(User.builder().username("access-alice").authProvider(AuthProvider.EMAIL).build());
        User bob = users.save(User.builder().username("access-bob").authProvider(AuthProvider.EMAIL).build());
        User outsider = users.save(User.builder().username("access-outsider").authProvider(AuthProvider.EMAIL).build());
        Friendship friendship = friendships.save(Friendship.builder().requester(alice).addressee(bob).status(FriendshipStatus.ACCEPTED).build());
        Conversation conversation = conversations.save(Conversation.builder().participantOne(alice).participantTwo(bob).build());
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), alice.getId(), alice.getUsername()).isPresent());
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), outsider.getId(), outsider.getUsername()).isEmpty());
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), alice.getId(), bob.getUsername()).isEmpty());
        alice.setUsername("access-alice-renamed");
        users.save(alice);
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), alice.getId(), "access-alice").isEmpty());
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), alice.getId(), alice.getUsername()).isPresent());
        friendships.delete(friendship);
        assertTrue(conversations.findAccessibleForSocket(conversation.getId(), alice.getId(), alice.getUsername()).isEmpty());
    }

    private void sendAndAwait(StompSession sender, Long conversationId, BlockingQueue<Map<String, Object>> received, String content) throws Exception {
        sender.send("/app/dm.sendMessage", Map.of("conversationId", conversationId, "content", content, "clientMessageId", content));
        Map<String, Object> delivered = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(delivered, "Message was not delivered");
        assertEquals(content, delivered.get("content"));
        assertNotNull(delivered.get("id"), "Recipient must receive a persisted message");
        assertEquals(content, delivered.get("clientMessageId"));
        assertTrue((Double) delivered.get("_serverMs") >= 0, "Server timing must reach the browser");
    }

    private StompSession connect(WebSocketStompClient client, User user) throws Exception {
        var headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + jwt.generateToken(user));
        return client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler handler(BlockingQueue<Map<String, Object>> received) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> decoded = (Map<String, Object>) payload;
                String timing = headers.getFirst("x-chat-server-ms");
                if (timing != null) decoded.put("_serverMs", Double.parseDouble(timing));
                received.add(decoded);
            }
        };
    }
}
