package com.theskysid.echobackend.messaging.repository;

import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.messaging.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /**
     * Find non-expired messages for a conversation, paginated and ordered by newest first.
     * The filter-on-read approach ensures users never see expired messages
     * even before the scheduled cleanup runs.
     */
    @Query(value = "SELECT dm FROM DirectMessage dm JOIN FETCH dm.sender JOIN FETCH dm.conversation c JOIN FETCH c.participantOne JOIN FETCH c.participantTwo WHERE dm.conversation = :conversation " +
            "AND dm.expiresAt > :now ORDER BY dm.timestamp DESC",
           countQuery = "SELECT COUNT(dm) FROM DirectMessage dm WHERE dm.conversation = :conversation AND dm.expiresAt > :now")
    Page<DirectMessage> findActiveByConversation(
            @Param("conversation") Conversation conversation,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * Delete all messages that have expired. Called by the scheduled cleanup task.
     */
    @Modifying
    @Query("DELETE FROM DirectMessage dm WHERE dm.expiresAt < :now")
    int deleteExpiredMessages(@Param("now") LocalDateTime now);

    /**
     * Check if a conversation has any remaining (non-expired) messages.
     */
    @Query("SELECT CASE WHEN COUNT(dm) > 0 THEN true ELSE false END " +
            "FROM DirectMessage dm WHERE dm.conversation = :conversation AND dm.expiresAt > :now")
    boolean hasActiveMessages(@Param("conversation") Conversation conversation, @Param("now") LocalDateTime now);
}
