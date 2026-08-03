package com.theskysid.echobackend.call.repository;

import com.theskysid.echobackend.call.entity.CallTranscript;
import com.theskysid.echobackend.channel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CallTranscriptRepository extends JpaRepository<CallTranscript, Long> {

    /**
     * Find all transcripts for a channel, most recent first.
     */
    @Query("SELECT t FROM CallTranscript t JOIN FETCH t.channel WHERE t.channel = :channel ORDER BY t.createdAt DESC")
    List<CallTranscript> findByChannel(@Param("channel") Channel channel);
}
