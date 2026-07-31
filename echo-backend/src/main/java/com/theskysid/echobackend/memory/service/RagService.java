package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.memory.dto.RagContextDTO;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    /**
     * Embed the query, fetch the top 5 most similar memory vectors for the given
     * channel only, and combine their raw text into a single context block.
     */
    public RagContextDTO retrieveContext(String channelId, String query) {
        if (query == null || query.isBlank()) {
            throw new RuntimeException("Query is required");
        }

        Long channel = Long.valueOf(channelId);
        float[] queryEmbedding = embeddingService.embed(query);
        String vectorLiteral = toVectorLiteral(queryEmbedding);

        List<MemoryVector> matches = memoryVectorRepository
                .findTop5ByChannelAndSimilarity(channel, vectorLiteral);

        String context = matches.stream()
                .map(MemoryVector::getContent)
                .collect(Collectors.joining("\n\n"));

        List<Long> sourceIds = matches.stream()
                .map(MemoryVector::getSourceId)
                .collect(Collectors.toList());

        return RagContextDTO.builder()
                .channelId(channel)
                .query(query)
                .context(context)
                .sourceIds(sourceIds)
                .build();
    }

    /**
     * Render a float[] as a pgvector literal, e.g. "[0.1,0.2,0.3]".
     */
    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
