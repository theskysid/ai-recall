package com.theskysid.echobackend.messaging.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.auth.service.OnlineUserService;
import com.theskysid.echobackend.messaging.dto.ConversationDTO;
import com.theskysid.echobackend.messaging.dto.DirectMessageDTO;
import com.theskysid.echobackend.messaging.dto.RetentionUpdateDTO;
import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.messaging.entity.DirectMessage;
import com.theskysid.echobackend.messaging.entity.RetentionPolicy;
import com.theskysid.echobackend.messaging.service.DirectMessageService;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * GET /api/conversations — list user's active conversations
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ConversationDTO> getConversations(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return directMessageService.getConversations(currentUser).stream()
                .map(conv -> toConversationDTO(conv, currentUser))
                .toList();
    }

    /**
     * GET /api/conversations/{id}/messages?page=0&size=50 — paginated active message history
     */
    @GetMapping("/{id}/messages")
    @Transactional(readOnly = true)
    public Page<DirectMessageDTO> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        Conversation conversation = directMessageService.getConversation(id, currentUser);
        return directMessageService.getMessages(conversation, currentUser, page, size)
                .map(this::toDirectMessageDTO);
    }

    /**
     * PUT /api/conversations/{id}/retention — update conversation retention policy
     */
    @PutMapping("/{id}/retention")
    @Transactional
    public ConversationDTO updateRetention(
            @PathVariable Long id,
            @RequestBody RetentionUpdateDTO request,
            Authentication authentication) {
        RetentionPolicy policy = parsePolicy(request.getPolicy());
        User currentUser = currentUser(authentication);
        Conversation updated = directMessageService.updateRetentionPolicy(id, currentUser, policy);

        User otherUser = updated.getOtherParticipant(currentUser);
        DirectMessageDTO notification = DirectMessageDTO.builder()
                .conversationId(updated.getId())
                .senderId(currentUser.getId())
                .senderUsername(currentUser.getUsername())
                .recipientUsername(otherUser.getUsername())
                .content("RETENTION_POLICY_UPDATE:" + policy.name())
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/user/" + otherUser.getUsername() + "/queue/dm", notification);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUsername() + "/queue/dm", notification);

        return toConversationDTO(updated, currentUser);
    }

    /**
     * POST /api/conversations/with/{username} — get or create a conversation with a friend
     */
    @PostMapping("/with/{username}")
    @Transactional
    public ConversationDTO getOrCreateConversationWithFriend(
            @PathVariable String username,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        User friend = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toConversationDTO(directMessageService.getOrCreateConversation(currentUser, friend), currentUser);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private User currentUser(Authentication authentication) {
        return authenticationService.resolveAuthenticatedUser(authentication.getName());
    }

    /**
     * Parsed here rather than left to the enum: valueOf's own message names the
     * Java constant, and the client is told which values it may send.
     */
    private RetentionPolicy parsePolicy(String policy) {
        for (RetentionPolicy candidate : RetentionPolicy.values()) {
            if (candidate.name().equalsIgnoreCase(policy)) {
                return candidate;
            }
        }
        throw new RuntimeException("Invalid retention policy. Must be SIX_HOURS, ONE_DAY, or SEVEN_DAYS");
    }

    private ConversationDTO toConversationDTO(Conversation conversation, User currentUser) {
        User otherUser = conversation.getOtherParticipant(currentUser);
        return ConversationDTO.builder()
                .id(conversation.getId())
                .otherUserId(otherUser.getId())
                .otherUsername(otherUser.getUsername())
                .otherDisplayName(otherUser.getDisplayName())
                .otherUserOnline(onlineUserService.isOnline(otherUser.getUsername()))
                .retentionPolicy(conversation.getRetentionPolicy().name())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private DirectMessageDTO toDirectMessageDTO(DirectMessage message) {
        User recipient = message.getConversation().getOtherParticipant(message.getSender());
        return DirectMessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .recipientUsername(recipient.getUsername())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .expiresAt(message.getExpiresAt())
                .build();
    }
}
