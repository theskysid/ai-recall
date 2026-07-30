package com.theskysid.echobackend.messaging.repository;

import com.theskysid.echobackend.messaging.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT cm from ChatMessage cm WHERE cm.type = 'PRIVATE_MESSAGE' AND " +
            "((cm.sender = :user1 AND cm.recipient = :user2) OR (cm.sender = :user2 AND cm.recipient = :user1))" +
            "ORDER BY cm.timestamp ASC")
    List<ChatMessage> findPrivateMessagesBetweenTwoUsers(@Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.type = 'CHAT' AND cm.timestamp >= :cutoff ORDER BY cm.timestamp ASC")
    List<ChatMessage> findGlobalMessagesSince(@Param("cutoff") java.time.LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.type = 'CHAT' AND cm.timestamp < :cutoff")
    void deleteOldGlobalMessages(@Param("cutoff") java.time.LocalDateTime cutoff);
}
