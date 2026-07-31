package com.theskysid.echobackend.channel.repository;

import com.theskysid.echobackend.channel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    /**
     * Find a channel by its shareable invite code.
     */
    @Query("SELECT c FROM Channel c JOIN FETCH c.owner WHERE c.inviteCode = :inviteCode")
    Optional<Channel> findByInviteCode(@Param("inviteCode") String inviteCode);

    /**
     * Check whether an invite code is already taken. Used to guarantee uniqueness.
     */
    boolean existsByInviteCode(String inviteCode);
}
