package com.theskysid.echobackend.messaging.websocket;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.messaging.entity.ChatMessage;
import com.theskysid.echobackend.messaging.repository.ChatMessageRepository;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipService friendshipService;

    @GetMapping("/private")
    public ResponseEntity<?> getPrivateMessages(
            @RequestParam String user1,
            @RequestParam String user2,
            Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        User firstUser = userRepository.findByUsernameIgnoreCase(user1).orElse(null);
        User secondUser = userRepository.findByUsernameIgnoreCase(user2).orElse(null);
        if (firstUser == null || secondUser == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        boolean isParticipant = currentUser.getId().equals(firstUser.getId())
                || currentUser.getId().equals(secondUser.getId());
        if (!isParticipant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You can only view your own private messages"));
        }

        if (!friendshipService.areFriends(firstUser, secondUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Private messages are only available between friends"));
        }

        List<ChatMessage> messages = chatMessageRepository.findPrivateMessagesBetweenTwoUsers(user1, user2);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/global")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> getGlobalMessages(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(7);
        try {
            chatMessageRepository.deleteOldGlobalMessages(cutoff);
        } catch (Exception e) {
            // Log and continue if cleanup has an issue
        }
        List<ChatMessage> messages = chatMessageRepository.findGlobalMessagesSince(cutoff);
        return ResponseEntity.ok(messages);
    }

}
