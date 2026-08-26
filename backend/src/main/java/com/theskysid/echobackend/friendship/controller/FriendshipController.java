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
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public List<FriendUserDTO> getFriends(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return friendshipRepository.findAcceptedFriendships(currentUser).stream()
                .map(friendship -> toFriendUserDTO(
                        otherParticipant(friendship, currentUser), "ACCEPTED", friendship.getId()))
                .toList();
    }

    /**
     * GET /api/friends/requests/incoming — list pending incoming requests
     */
    @GetMapping("/requests/incoming")
    @Transactional(readOnly = true)
    public List<FriendshipDTO> getIncomingRequests(Authentication authentication) {
        return friendshipService.getIncomingRequests(currentUser(authentication)).stream()
                .map(this::toFriendshipDTO)
                .toList();
    }

    /**
     * GET /api/friends/requests/rejected — list rejected requests
     */
    @GetMapping("/requests/rejected")
    @Transactional(readOnly = true)
    public List<FriendshipDTO> getRejectedRequests(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return friendshipService.getRejectedRequests(currentUser).stream()
                .map(friendship -> FriendshipDTO.builder()
                        .id(friendship.getId())
                        .requesterUsername(currentUser.getUsername())
                        .addresseeUsername(otherParticipant(friendship, currentUser).getUsername())
                        .status(friendship.getStatus().name())
                        .createdAt(friendship.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * GET /api/friends/search?q={query} — search users by username
     */
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<FriendUserDTO> searchUsers(@RequestParam("q") String query, Authentication authentication) {
        User currentUser = currentUser(authentication);
        return friendshipService.searchUsers(currentUser, query).stream()
                .map(user -> {
                    Optional<Friendship> friendship = friendshipRepository.findBetweenUsers(currentUser, user);
                    return toFriendUserDTO(
                            user,
                            resolveRelationshipStatus(currentUser, friendship),
                            friendship.map(Friendship::getId).orElse(null));
                })
                .toList();
    }

    /**
     * POST /api/friends/request — send a friend request
     */
    @PostMapping("/request")
    public FriendshipDTO sendFriendRequest(@RequestBody FriendRequestDTO request, Authentication authentication) {
        return toFriendshipDTO(
                friendshipService.sendFriendRequest(currentUser(authentication), request.getAddresseeUsername()));
    }

    /**
     * POST /api/friends/accept/{id} — accept an incoming request
     */
    @PostMapping("/accept/{id}")
    public FriendshipDTO acceptRequest(@PathVariable Long id, Authentication authentication) {
        return toFriendshipDTO(friendshipService.acceptFriendRequest(currentUser(authentication), id));
    }

    /**
     * POST /api/friends/reject/{id} — reject an incoming request
     */
    @PostMapping("/reject/{id}")
    public FriendshipDTO rejectRequest(@PathVariable Long id, Authentication authentication) {
        return toFriendshipDTO(friendshipService.rejectFriendRequest(currentUser(authentication), id));
    }

    /**
     * DELETE /api/friends/cancel/{id} — cancel an outgoing request
     */
    @DeleteMapping("/cancel/{id}")
    public Map<String, String> cancelRequest(@PathVariable Long id, Authentication authentication) {
        friendshipService.cancelFriendRequest(currentUser(authentication), id);
        return Map.of("message", "Friend request cancelled");
    }

    /**
     * DELETE /api/friends/{id} — remove a friend
     */
    @DeleteMapping("/{id}")
    public Map<String, String> removeFriend(@PathVariable Long id, Authentication authentication) {
        friendshipService.removeFriend(currentUser(authentication), id);
        return Map.of("message", "Friend removed");
    }

    // ── Helpers ─────────────────────────────────────────────────

    private User currentUser(Authentication authentication) {
        return authenticationService.resolveAuthenticatedUser(authentication.getName());
    }

    private User otherParticipant(Friendship friendship, User currentUser) {
        return friendship.getRequester().getId().equals(currentUser.getId())
                ? friendship.getAddressee()
                : friendship.getRequester();
    }

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
