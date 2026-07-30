package com.theskysid.echobackend.friendship.repository;

import com.theskysid.echobackend.friendship.entity.Friendship;
import com.theskysid.echobackend.friendship.entity.FriendshipStatus;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * Find all accepted friendships where the user is either requester or addressee.
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester JOIN FETCH f.addressee WHERE f.status = 'ACCEPTED' " +
            "AND (f.requester = :user OR f.addressee = :user)")
    List<Friendship> findAcceptedFriendships(@Param("user") User user);

    /**
     * Find pending requests where the user is the addressee (incoming requests).
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester JOIN FETCH f.addressee WHERE f.addressee = :addressee AND f.status = :status")
    List<Friendship> findByAddresseeAndStatus(@Param("addressee") User addressee, @Param("status") FriendshipStatus status);

    /**
     * Find rejected requests where the user is either the requester or addressee.
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester JOIN FETCH f.addressee WHERE (f.requester = :user OR f.addressee = :user) AND f.status = 'REJECTED'")
    List<Friendship> findRejectedRequests(@Param("user") User user);

    /**
     * Check if any friendship row exists between two users in either direction.
     * Used to prevent duplicate or conflicting requests.
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester JOIN FETCH f.addressee WHERE " +
            "(f.requester = :userA AND f.addressee = :userB) OR " +
            "(f.requester = :userB AND f.addressee = :userA)")
    Optional<Friendship> findBetweenUsers(@Param("userA") User userA, @Param("userB") User userB);

    /**
     * Check if two users are friends (accepted friendship exists in either direction).
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
            "WHERE f.status = 'ACCEPTED' AND " +
            "((f.requester = :userA AND f.addressee = :userB) OR " +
            "(f.requester = :userB AND f.addressee = :userA))")
    boolean areFriends(@Param("userA") User userA, @Param("userB") User userB);
}
