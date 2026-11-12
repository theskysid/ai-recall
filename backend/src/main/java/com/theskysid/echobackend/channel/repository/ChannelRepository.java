package com.theskysid.echobackend.channel.repository;

import com.theskysid.echobackend.channel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Channel c WHERE c.id = :id")
    Optional<Channel> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM Channel c WHERE c.id = :id")
    Optional<Channel> findByIdForShare(@Param("id") Long id);

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
