package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.call.entity.CallTranscript;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.entity.SourceType;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryIngestionService.class);

    private static final int CHUNK_WORD_LIMIT = 300;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    @Autowired
    private DecisionService decisionService;

    /**
     * Embed a channel message and store it as a MEMORY vector. Runs off the
     * main thread so it never blocks the WebSocket broadcast.
     */
    @Async
    public void ingestMessage(ChannelMessage message) {
        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            return;
        }
        try {
            float[] embedding = embeddingService.embed(message.getContent());
            MemoryVector vector = MemoryVector.builder()
                    .channelId(message.getChannel().getId())
                    .content(message.getContent())
                    .embedding(embedding)
                    .sourceType(SourceType.MESSAGE)
                    .sourceId(message.getId())
                    .build();
            decisionService.processSupersession(vector);
            memoryVectorRepository.save(vector);
        } catch (Exception e) {
            logger.warn("Failed to ingest message {} into memory: {}", message.getId(), e.getMessage());
        }
    }

    /**
     * Split a transcript into chunks, embed each, and store them as TRANSCRIPT
     * vectors. Runs off the main thread.
     */
    @Async
    public void ingestTranscript(CallTranscript transcript) {
        if (transcript == null || transcript.getFullTranscript() == null || transcript.getFullTranscript().isBlank()) {
            return;
        }
        try {
            List<String> chunks = chunk(transcript.getFullTranscript());
            for (String chunk : chunks) {
                if (chunk.isBlank()) {
                    continue;
                }
                float[] embedding = embeddingService.embed(chunk);
                MemoryVector vector = MemoryVector.builder()
                        .channelId(transcript.getChannel().getId())
                        .content(chunk)
                        .embedding(embedding)
                        .sourceType(SourceType.TRANSCRIPT)
                        .sourceId(transcript.getId())
                        .build();
                decisionService.processSupersession(vector);
                memoryVectorRepository.save(vector);
            }
        } catch (Exception e) {
            logger.warn("Failed to ingest transcript {} into memory: {}", transcript.getId(), e.getMessage());
        }
    }

    /**
     * Split text into logical chunks: first on blank lines (paragraphs), then
     * further split any paragraph longer than CHUNK_WORD_LIMIT words.
     */
    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] words = trimmed.split("\\s+");
            if (words.length <= CHUNK_WORD_LIMIT) {
                chunks.add(trimmed);
                continue;
            }
            StringBuilder block = new StringBuilder();
            int count = 0;
            for (String word : words) {
                block.append(word).append(' ');
                if (++count == CHUNK_WORD_LIMIT) {
                    chunks.add(block.toString().trim());
                    block.setLength(0);
                    count = 0;
                }
            }
            if (block.length() > 0) {
                chunks.add(block.toString().trim());
            }
        }
        return chunks;
    }
}
