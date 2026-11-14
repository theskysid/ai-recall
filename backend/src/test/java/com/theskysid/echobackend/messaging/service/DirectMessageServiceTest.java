package com.theskysid.echobackend.messaging.service;

import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.messaging.entity.*;
import com.theskysid.echobackend.messaging.repository.*;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {
    @Mock DirectMessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock FriendshipService friendships;
    @Mock UserRepository users;
    @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @InjectMocks DirectMessageService service;

    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();
    Conversation conversation;

    @BeforeEach void setup() {
        conversation = Conversation.builder().id(7L).participantOne(alice).participantTwo(bob)
                .retentionPolicy(RetentionPolicy.ONE_DAY).build();
    }

    @Test void socketSendAuthorizesOnceAndUsesThePersistedConversation() {
        when(conversations.findAccessibleForSocket(7L, 1L, "alice")).thenReturn(Optional.of(conversation));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DirectMessage saved = service.sendMessage(7L, 1L, "alice", "untrusted-recipient", "hello");
        assertSame(alice, saved.getSender());
        assertSame(conversation, saved.getConversation());
        verify(conversations).findAccessibleForSocket(7L, 1L, "alice");
        verifyNoInteractions(friendships, users);
    }

    @Test void inaccessibleConversationCannotBeWritten() {
        when(conversations.findAccessibleForSocket(7L, 1L, "alice")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.sendMessage(7L, 1L, "alice", null, "hello"));
        verifyNoInteractions(messages, friendships, users);
        verify(conversations, never()).save(any());
    }

    @Test void conventionalServiceEntryStillChecksFriendship() {
        when(friendships.areFriends(alice, bob)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.sendMessage(conversation, alice, "hello"));
        verify(friendships).areFriends(alice, bob);
        verifyNoInteractions(messages);
    }
}
