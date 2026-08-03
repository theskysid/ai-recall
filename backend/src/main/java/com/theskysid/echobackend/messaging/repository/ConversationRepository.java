package com.theskysid.echobackend.messaging.repository;

import com.theskysid.echobackend.messaging.entity.Conversation;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Find a conversation between two specific users.
     * Participants are always stored with smaller ID as participantOne.
     */
    @Query("SELECT c FROM Conversation c JOIN FETCH c.participantOne JOIN FETCH c.participantTwo WHERE c.participantOne = :userA AND c.participantTwo = :userB")
    Optional<Conversation> findByParticipants(@Param("userA") User userA, @Param("userB") User userB);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.participantOne JOIN FETCH c.participantTwo WHERE c.id = :id")
    Optional<Conversation> findByIdWithParticipants(@Param("id") Long id);

    /**
     * Find all conversations that a user participates in, ordered by most recent activity.
     */
    @Query("SELECT DISTINCT c FROM Conversation c JOIN FETCH c.participantOne JOIN FETCH c.participantTwo WHERE c.participantOne = :user OR c.participantTwo = :user " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findByParticipant(@Param("user") User user);

    /**
     * Delete conversations that have zero remaining messages (all expired and cleaned up).
     */
    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.id NOT IN " +
            "(SELECT DISTINCT dm.conversation.id FROM DirectMessage dm)")
    int deleteConversationsWithNoMessages();
}
