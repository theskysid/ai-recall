package com.theskysid.echobackend.config;

import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthorizationInterceptorTest {
    @Mock JwtService jwtService;
    @Mock UserRepository users;
    @Mock ChannelService channels;
    @InjectMocks WebSocketAuthorizationInterceptor interceptor;
    User alice;

    @BeforeEach
    void setup() {
        alice = User.builder().id(1L).username("alice").build();
    }

    @Test
    void anonymousConnectCannotClaimAUsername() {
        var headers = headers(StompCommand.CONNECT, null, null);
        headers.setNativeHeader("username", "alice");
        assertThrows(AccessDeniedException.class, () -> inbound(headers));
    }

    @Test
    void connectUsesVerifiedTokenIdentity() {
        when(jwtService.extractUserId("valid")).thenReturn(1L);
        when(users.findById(1L)).thenReturn(Optional.of(alice));
        when(jwtService.isTokenValid("valid", alice)).thenReturn(true);
        var headers = headers(StompCommand.CONNECT, null, null);
        headers.setNativeHeader("Authorization", "Bearer valid");
        headers.setNativeHeader("username", "victim");
        inbound(headers);
        assertEquals("alice", headers.getUser().getName());
    }

    @Test
    void invalidBearerCannotFallBackToCookiePrincipal() {
        when(jwtService.extractUserId("expired")).thenThrow(new IllegalArgumentException("expired"));
        var headers = headers(StompCommand.CONNECT, null, () -> "alice");
        headers.setNativeHeader("Authorization", "Bearer expired");
        assertThrows(AccessDeniedException.class, () -> inbound(headers));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/victim/queue/dm", "/user/alice/queue/dm", "/queue/dm-userother", "/topic/channel/*", "/topic/unknown"})
    void arbitrarySubscriptionsAreDenied(String destination) {
        connect();
        assertThrows(AccessDeniedException.class, () -> inbound(headers(StompCommand.SUBSCRIBE, destination, () -> "alice")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/public", "/user/queue/dm", "/user/queue/friends", "/topic/channel/42"})
    void authenticatedSubscriptionsAreAllowed(String destination) {
        connect();
        var message = inbound(headers(StompCommand.SUBSCRIBE, destination, () -> "alice"));
        assertNotNull(message);
        if (destination.startsWith("/topic/channel/")) verify(channels).requireMember(1L, "alice", 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/channel/42", "/user/victim/queue/dm", "/queue/dm-userother", "/topic/public"})
    void clientsCannotPublishDirectlyToBroker(String destination) {
        connect();
        assertThrows(AccessDeniedException.class, () -> inbound(headers(StompCommand.SEND, destination, () -> "alice")));
    }

    @Test
    void nonMembersCannotSubscribeOrSendAnyChannelEvent() {
        connect();
        doThrow(new IllegalStateException("not a member")).when(channels).requireMember(1L, "alice", 42L);
        assertThrows(AccessDeniedException.class, () -> inbound(headers(StompCommand.SUBSCRIBE, "/topic/channel/42", () -> "alice")));
        assertThrows(AccessDeniedException.class, () -> inbound(headers(StompCommand.SEND, "/app/channel/42/send", () -> "alice")));
    }

    @Test
    void existingSubscriptionsStopReceivingAfterLeave() {
        connect();
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/topic/channel/42");
        headers.setSessionId("session");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertSame(message, interceptor.authorizeDelivery(message));
        doThrow(new IllegalStateException("left")).when(channels).requireMember(1L, "alice", 42L);
        assertNull(interceptor.authorizeDelivery(message));
    }

    @Test
    void disconnectRevokesSessionAccess() {
        connect();
        inbound(headers(StompCommand.DISCONNECT, null, () -> "alice"));
        assertThrows(AccessDeniedException.class, () -> inbound(headers(StompCommand.SUBSCRIBE, "/topic/public", () -> "alice")));
    }

    @Test
    void typingFloodIsDroppedBeforeDatabaseWorkAndClaimedIdsAreOverwritten() {
        connect();
        clearInvocations(users, channels);
        var first = headers(StompCommand.SEND, "/app/dm.typing", () -> "alice");
        first.setHeader(WebSocketAuthorizationInterceptor.AUTHENTICATED_USER_ID, 999L);
        assertNotNull(inbound(first));
        assertEquals(1L, first.getHeader(WebSocketAuthorizationInterceptor.AUTHENTICATED_USER_ID));
        for (int i = 0; i < 100; i++) {
            assertNull(inbound(headers(StompCommand.SEND, "/app/dm.typing", () -> "alice")));
        }
        verifyNoInteractions(users, channels);
    }

    @Test
    void channelAuthorizationUsesOneMembershipQuery() {
        connect();
        clearInvocations(users, channels);
        inbound(headers(StompCommand.SUBSCRIBE, "/topic/channel/42", () -> "alice"));
        verify(channels).requireMember(1L, "alice", 42L);
        verifyNoInteractions(users);
    }

    @Test
    void renamedAccountsCannotReceiveTheirOldPrivateQueue() {
        connect();
        when(users.existsByIdAndUsername(1L, "alice")).thenReturn(false);
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/queue/dm-usersession");
        headers.setSessionId("session");
        assertNull(interceptor.authorizeDelivery(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders())));
    }

    private void connect() {
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
        lenient().when(users.existsByIdAndUsername(1L, "alice")).thenReturn(true);
        inbound(headers(StompCommand.CONNECT, null,
                new UsernamePasswordAuthenticationToken("alice", null, java.util.List.of())));
    }

    private StompHeaderAccessor headers(StompCommand command, String destination, Principal principal) {
        var headers = command == null ? StompHeaderAccessor.createForHeartbeat() : StompHeaderAccessor.create(command);
        headers.setSessionId("session");
        headers.setDestination(destination);
        headers.setUser(principal);
        return headers;
    }

    private Message<?> inbound(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        return interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }
}
