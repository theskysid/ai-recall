package com.theskysid.echobackend.friendship.service;

import com.theskysid.echobackend.friendship.entity.Friendship;
import com.theskysid.echobackend.friendship.entity.FriendshipStatus;
import com.theskysid.echobackend.friendship.repository.FriendshipRepository;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private void sendFriendEvent(User targetUser, String type, String otherUsername) {
        try {
            if (targetUser == null) {
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    targetUser.getUsername(),
                    "/queue/friends",
                    Map.of(
                            "type", type,
                            "username", otherUsername == null ? "" : otherUsername));
        } catch (Exception e) {
            // Log but allow transaction to finish cleanly
        }
    }

    /**
     * Send a friend request from the current user to the addressee.
     */
    @Transactional
    public Friendship sendFriendRequest(User requester, String addresseeUsername) {
        if (requester.getUsername().equalsIgnoreCase(addresseeUsername)) {
            throw new RuntimeException("Cannot send a friend request to yourself");
        }

        User addressee = userRepository.findByUsernameIgnoreCase(addresseeUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        friendshipRepository.findBetweenUsers(requester, addressee).ifPresent(existing -> {
            switch (existing.getStatus()) {
                case ACCEPTED -> throw new RuntimeException("You are already friends");
                case PENDING -> {
                    if (existing.getRequester().getId().equals(requester.getId())) {
                        throw new RuntimeException("Friend request already sent");
                    } else {
                        throw new RuntimeException("This user has already sent you a friend request");
                    }
                }
                case REJECTED -> {
                    // Allow re-sending after rejection — clean up the old row
                    friendshipRepository.delete(existing);
                    friendshipRepository.flush();
                }
            }
        });

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship saved = friendshipRepository.save(friendship);
        friendshipRepository.flush();
        sendFriendEvent(requester, "FRIEND_REQUEST_SENT", addressee.getUsername());
        sendFriendEvent(addressee, "FRIEND_REQUEST_RECEIVED", requester.getUsername());
        return saved;
    }

    /**
     * Accept an incoming friend request. Only the addressee can accept.
     */
    @Transactional
    public Friendship acceptFriendRequest(User currentUser, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the recipient can accept a friend request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("This request is no longer pending");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        Friendship saved = friendshipRepository.save(friendship);
        friendshipRepository.flush();
        sendFriendEvent(friendship.getRequester(), "FRIEND_REQUEST_ACCEPTED", friendship.getAddressee().getUsername());
        sendFriendEvent(friendship.getAddressee(), "FRIEND_REQUEST_ACCEPTED_BY_YOU", friendship.getRequester().getUsername());
        return saved;
    }

    /**
     * Reject an incoming friend request. Only the addressee can reject.
     */
    @Transactional
    public Friendship rejectFriendRequest(User currentUser, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (!friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the recipient can reject a friend request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("This request is no longer pending");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        Friendship saved = friendshipRepository.save(friendship);
        friendshipRepository.flush();
        sendFriendEvent(friendship.getRequester(), "FRIEND_REQUEST_REJECTED", friendship.getAddressee().getUsername());
        sendFriendEvent(friendship.getAddressee(), "FRIEND_REQUEST_REJECTED_BY_YOU", friendship.getRequester().getUsername());
        return saved;
    }

    /**
     * Cancel an outgoing friend request or remove from rejected list.
     */
    @Transactional
    public void cancelFriendRequest(User currentUser, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        if (friendship.getStatus() == FriendshipStatus.REJECTED) {
            boolean isParticipant = friendship.getRequester().getId().equals(currentUser.getId())
                    || friendship.getAddressee().getId().equals(currentUser.getId());
            if (!isParticipant) {
                throw new RuntimeException("You are not part of this request");
            }
            User uA = friendship.getRequester();
            User uB = friendship.getAddressee();
            friendshipRepository.delete(friendship);
            friendshipRepository.flush();
            sendFriendEvent(uA, "FRIEND_REQUEST_CANCELLED", uB.getUsername());
            sendFriendEvent(uB, "FRIEND_REQUEST_CANCELLED", uA.getUsername());
            return;
        }

        if (!friendship.getRequester().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Only the sender can cancel a friend request");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("This request is no longer pending");
        }

        User uA = friendship.getRequester();
        User uB = friendship.getAddressee();
        friendshipRepository.delete(friendship);
        friendshipRepository.flush();
        sendFriendEvent(uA, "FRIEND_REQUEST_CANCELLED_BY_YOU", uB.getUsername());
        sendFriendEvent(uB, "FRIEND_REQUEST_CANCELLED", uA.getUsername());
    }

    /**
     * Remove an existing friendship. Either party can remove.
     */
    @Transactional
    public void removeFriend(User currentUser, Long friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friendship not found"));

        boolean isParticipant = friendship.getRequester().getId().equals(currentUser.getId())
                || friendship.getAddressee().getId().equals(currentUser.getId());

        if (!isParticipant) {
            throw new RuntimeException("You are not part of this friendship");
        }

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new RuntimeException("This friendship is not active");
        }

        User uA = friendship.getRequester();
        User uB = friendship.getAddressee();
        friendshipRepository.delete(friendship);
        friendshipRepository.flush();
        sendFriendEvent(uA, "FRIEND_REMOVED", uB.getUsername());
        sendFriendEvent(uB, "FRIEND_REMOVED", uA.getUsername());
    }

    /**
     * Get all accepted friends for a user.
     * Returns the "other" user in each friendship.
     */
    @Transactional(readOnly = true)
    public List<User> getFriends(User user) {
        return friendshipRepository.findAcceptedFriendships(user).stream()
                .map(f -> f.getRequester().getId().equals(user.getId())
                        ? f.getAddressee()
                        : f.getRequester())
                .collect(Collectors.toList());
    }

    /**
     * Get all pending incoming requests for the current user.
     */
    @Transactional(readOnly = true)
    public List<Friendship> getIncomingRequests(User user) {
        return friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
    }

    /**
     * Get all rejected requests for the current user.
     */
    @Transactional(readOnly = true)
    public List<Friendship> getRejectedRequests(User user) {
        return friendshipRepository.findRejectedRequests(user);
    }

    /**
     * Search users by username. Excludes the current user from results.
     */
    @Transactional(readOnly = true)
    public List<User> searchUsers(User currentUser, String query) {
        if (query == null || query.trim().length() < 2) {
            throw new RuntimeException("Search query must be at least 2 characters");
        }

        return userRepository.searchByUsername(query.trim()).stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Check if two users are friends. Used by the DM system.
     */
    @Transactional(readOnly = true)
    public boolean areFriends(User userA, User userB) {
        return friendshipRepository.areFriends(userA, userB);
    }
}
