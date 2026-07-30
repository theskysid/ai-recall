package com.theskysid.echobackend.messaging.service;

import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.messaging.entity.DirectMessage;
import com.theskysid.echobackend.messaging.entity.RetentionPolicy;
import com.theskysid.echobackend.messaging.repository.ConversationRepository;
import com.theskysid.echobackend.messaging.repository.DirectMessageRepository;
import com.theskysid.echobackend.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DirectMessageService {

    private static final Logger logger = LoggerFactory.getLogger(DirectMessageService.class);

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private FriendshipService friendshipService;

    /**
     * Get or create a conversation between two users.
     * Always stores the user with the smaller ID as participantOne.
     */
    @Transactional
    public Conversation getOrCreateConversation(User userA, User userB) {
        if (!friendshipService.areFriends(userA, userB)) {
            throw new RuntimeException("You can only message friends");
        }

        User first = userA.getId() < userB.getId() ? userA : userB;
        User second = userA.getId() < userB.getId() ? userB : userA;

        return conversationRepository.findByParticipants(first, second)
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .participantOne(first)
                            .participantTwo(second)
                            .retentionPolicy(RetentionPolicy.ONE_DAY)
                            .build();
                    return conversationRepository.save(conversation);
                });
    }

    /**
     * Send a direct message within a conversation.
     */
    @Transactional
    public DirectMessage sendMessage(Conversation conversation, User sender, String content) {
        assertConversationAccess(conversation, sender);

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Message content cannot be empty");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(conversation.getRetentionPolicy().getHours());

        DirectMessage message = DirectMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content.trim())
                .timestamp(now)
                .expiresAt(expiresAt)
                .build();

        DirectMessage saved = directMessageRepository.save(message);

        // Update conversation's updatedAt to reflect latest activity
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        // Ensure participants are initialized
        conversation.getParticipantOne().getUsername();
        conversation.getParticipantTwo().getUsername();

        return saved;
    }

    /**
     * Get paginated message history for a conversation.
     * Only returns non-expired messages (filter-on-read).
     */
    @Transactional(readOnly = true)
    public Page<DirectMessage> getMessages(Conversation conversation, User currentUser, int page, int size) {
        assertConversationAccess(conversation, currentUser);

        return directMessageRepository.findActiveByConversation(
                conversation, LocalDateTime.now(), PageRequest.of(page, size));
    }

    /**
     * Get all conversations for a user, ordered by most recent activity.
     */
    @Transactional(readOnly = true)
    public List<Conversation> getConversations(User user) {
        return conversationRepository.findByParticipant(user).stream()
                .filter(conversation -> friendshipService.areFriends(user, conversation.getOtherParticipant(user)))
                .toList();
    }

    /**
     * Get a conversation by ID, verifying the user is a participant.
     */
    @Transactional(readOnly = true)
    public Conversation getConversation(Long conversationId, User currentUser) {
        Conversation conversation = conversationRepository.findByIdWithParticipants(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        assertConversationAccess(conversation, currentUser);
        return conversation;
    }

    /**
     * Update the retention policy for a conversation.
     * Either participant can change the retention policy.
     */
    @Transactional
    public Conversation updateRetentionPolicy(Long conversationId, User currentUser, RetentionPolicy policy) {
        Conversation conversation = getConversation(conversationId, currentUser);
        conversation.setRetentionPolicy(policy);
        return conversationRepository.save(conversation);
    }

    /**
     * Scheduled cleanup: delete expired messages and empty conversations.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cleanupExpiredMessages() {
        LocalDateTime now = LocalDateTime.now();

        int deletedMessages = directMessageRepository.deleteExpiredMessages(now);
        if (deletedMessages > 0) {
            logger.info("Cleaned up {} expired direct messages", deletedMessages);
        }

        int deletedConversations = conversationRepository.deleteConversationsWithNoMessages();
        if (deletedConversations > 0) {
            logger.info("Cleaned up {} empty conversations", deletedConversations);
        }
    }

    private void assertConversationAccess(Conversation conversation, User currentUser) {
        if (!conversation.hasParticipant(currentUser)) {
            throw new RuntimeException("You are not a participant in this conversation");
        }

        User otherParticipant = conversation.getOtherParticipant(currentUser);
        if (!friendshipService.areFriends(currentUser, otherParticipant)) {
            throw new RuntimeException("You can only access conversations with current friends");
        }
    }
}
