package com.theskysid.echobackend.channel.repository;

import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMembership;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelMembershipRepository extends JpaRepository<ChannelMembership, Long> {

    /**
     * Find all memberships for a user, i.e. the channels they currently belong to,
     * ordered by most recently joined first.
     */
    @Query("SELECT m FROM ChannelMembership m JOIN FETCH m.channel c JOIN FETCH c.owner WHERE m.user = :user " +
            "ORDER BY m.joinedAt DESC")
    List<ChannelMembership> findByUser(@Param("user") User user);

    /**
     * Find all memberships of a channel, ordered by longest-standing member first.
     * Used to transfer ownership when the owner leaves.
     */
    @Query("SELECT m FROM ChannelMembership m JOIN FETCH m.user WHERE m.channel = :channel " +
            "ORDER BY m.joinedAt ASC, m.id ASC")
    List<ChannelMembership> findByChannelOrderByJoinedAtAsc(@Param("channel") Channel channel);

    /**
     * Find a specific user's membership in a channel.
     */
    @Query("SELECT m FROM ChannelMembership m WHERE m.channel = :channel AND m.user = :user")
    Optional<ChannelMembership> findByChannelAndUser(@Param("channel") Channel channel, @Param("user") User user);

    /**
     * Whether a user is already a member of a channel.
     */
    boolean existsByChannelAndUser(Channel channel, User user);

    /**
     * Number of members currently in a channel.
     */
    long countByChannel(Channel channel);
}
