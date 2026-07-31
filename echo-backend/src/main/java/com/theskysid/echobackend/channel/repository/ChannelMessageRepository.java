package com.theskysid.echobackend.channel.repository;

import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {

    /**
     * Find all persisted CHAT messages for a channel, oldest first.
     */
    @Query("SELECT cm FROM ChannelMessage cm JOIN FETCH cm.sender JOIN FETCH cm.channel " +
            "WHERE cm.channel = :channel AND cm.type = 'CHAT' ORDER BY cm.timestamp ASC")
    List<ChannelMessage> findChannelMessages(@Param("channel") Channel channel);
}
