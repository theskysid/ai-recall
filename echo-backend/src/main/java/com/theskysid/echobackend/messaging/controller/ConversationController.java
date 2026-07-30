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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public ResponseEntity<?> getConversations(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            List<ConversationDTO> dtos = directMessageService.getConversations(currentUser).stream()
                    .map(conv -> toConversationDTO(conv, currentUser))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/conversations/{id}/messages?page=0&size=50 — paginated active message history
     */
    @GetMapping("/{id}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Conversation conversation = directMessageService.getConversation(id, currentUser);
            Page<DirectMessageDTO> messagePage = directMessageService.getMessages(conversation, currentUser, page, size)
                    .map(this::toDirectMessageDTO);
            return ResponseEntity.ok(messagePage);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/conversations/{id}/retention — update conversation retention policy
     */
    @PutMapping("/{id}/retention")
    @Transactional
    public ResponseEntity<?> updateRetention(
            @PathVariable Long id,
            @RequestBody RetentionUpdateDTO request,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            if (request.getPolicy() == null) {
                throw new RuntimeException("Policy is required");
            }
            RetentionPolicy policy = RetentionPolicy.valueOf(request.getPolicy().toUpperCase());
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
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

            return ResponseEntity.ok(toConversationDTO(updated, currentUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid retention policy. Must be SIX_HOURS, ONE_DAY, or SEVEN_DAYS"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/conversations/with/{username} — get or create a conversation with a friend
     */
    @PostMapping("/with/{username}")
    @Transactional
    public ResponseEntity<?> getOrCreateConversationWithFriend(
            @PathVariable String username,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            User friend = userRepository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Conversation conversation = directMessageService.getOrCreateConversation(currentUser, friend);
            return ResponseEntity.ok(toConversationDTO(conversation, currentUser));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

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
