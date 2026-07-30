package com.theskysid.echobackend.friendship.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.auth.service.OnlineUserService;
import com.theskysid.echobackend.friendship.dto.FriendRequestDTO;
import com.theskysid.echobackend.friendship.dto.FriendUserDTO;
import com.theskysid.echobackend.friendship.dto.FriendshipDTO;
import com.theskysid.echobackend.friendship.entity.Friendship;
import com.theskysid.echobackend.friendship.entity.FriendshipStatus;
import com.theskysid.echobackend.friendship.repository.FriendshipRepository;
import com.theskysid.echobackend.friendship.service.FriendshipService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    /**
     * GET /api/friends — list all accepted friends
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getFriends(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        List<FriendUserDTO> friends = friendshipRepository.findAcceptedFriendships(currentUser).stream()
                .map(friendship -> {
                    User otherUser = friendship.getRequester().getId().equals(currentUser.getId())
                            ? friendship.getAddressee()
                            : friendship.getRequester();
                    return toFriendUserDTO(otherUser, "ACCEPTED", friendship.getId());
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(friends);
    }

    /**
     * GET /api/friends/requests/incoming — list pending incoming requests
     */
    @GetMapping("/requests/incoming")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getIncomingRequests(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        List<FriendshipDTO> requests = friendshipService.getIncomingRequests(currentUser).stream()
                .map(this::toFriendshipDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(requests);
    }

    /**
     * GET /api/friends/requests/rejected — list rejected requests
     */
    @GetMapping("/requests/rejected")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRejectedRequests(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        List<FriendshipDTO> requests = friendshipService.getRejectedRequests(currentUser).stream()
                .map(friendship -> {
                    User otherUser = friendship.getRequester().getId().equals(currentUser.getId())
                            ? friendship.getAddressee()
                            : friendship.getRequester();
                    return FriendshipDTO.builder()
                            .id(friendship.getId())
                            .requesterUsername(currentUser.getUsername())
                            .addresseeUsername(otherUser.getUsername())
                            .status(friendship.getStatus().name())
                            .createdAt(friendship.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(requests);
    }

    /**
     * GET /api/friends/search?q={query} — search users by username
     */
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<?> searchUsers(@RequestParam("q") String query, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            List<FriendUserDTO> results = friendshipService.searchUsers(currentUser, query).stream()
                .map(user -> {
                        Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(currentUser, user);
                        String status = resolveRelationshipStatus(currentUser, friendship);
                        Long friendshipId = friendship.map(Friendship::getId).orElse(null);
                        return toFriendUserDTO(user, status, friendshipId);
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/friends/request — send a friend request
     */
    @PostMapping("/request")
    public ResponseEntity<?> sendFriendRequest(@RequestBody FriendRequestDTO request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Friendship friendship = friendshipService.sendFriendRequest(currentUser, request.getAddresseeUsername());
            return ResponseEntity.ok(toFriendshipDTO(friendship));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/friends/accept/{id} — accept an incoming request
     */
    @PostMapping("/accept/{id}")
    public ResponseEntity<?> acceptRequest(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Friendship friendship = friendshipService.acceptFriendRequest(currentUser, id);
            return ResponseEntity.ok(toFriendshipDTO(friendship));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/friends/reject/{id} — reject an incoming request
     */
    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectRequest(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Friendship friendship = friendshipService.rejectFriendRequest(currentUser, id);
            return ResponseEntity.ok(toFriendshipDTO(friendship));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/friends/cancel/{id} — cancel an outgoing request
     */
    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<?> cancelRequest(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            friendshipService.cancelFriendRequest(currentUser, id);
            return ResponseEntity.ok(Map.of("message", "Friend request cancelled"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/friends/{id} — remove a friend
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeFriend(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            friendshipService.removeFriend(currentUser, id);
            return ResponseEntity.ok(Map.of("message", "Friend removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private FriendshipDTO toFriendshipDTO(Friendship friendship) {
        return FriendshipDTO.builder()
                .id(friendship.getId())
                .requesterUsername(friendship.getRequester().getUsername())
                .addresseeUsername(friendship.getAddressee().getUsername())
                .status(friendship.getStatus().name())
                .createdAt(friendship.getCreatedAt())
                .build();
    }

    private FriendUserDTO toFriendUserDTO(User user, String friendshipStatus, Long friendshipId) {
        return FriendUserDTO.builder()
                .id(user.getId())
                .friendshipId(friendshipId)
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .online(onlineUserService.isOnline(user.getUsername()))
                .friendshipStatus(friendshipStatus)
                .build();
    }

    /**
     * Determine the relationship status between two users for search results.
     */
    private String resolveRelationshipStatus(User currentUser, Optional<Friendship> friendship) {
        if (friendship.isEmpty()) {
            return "NONE";
        }
        Friendship f = friendship.get();
        if (f.getStatus() == FriendshipStatus.ACCEPTED) {
            return "ACCEPTED";
        }
        if (f.getStatus() == FriendshipStatus.PENDING) {
            return f.getRequester().getId().equals(currentUser.getId())
                    ? "PENDING_OUTGOING"
                    : "PENDING_INCOMING";
        }
        return "NONE";
    }
}
