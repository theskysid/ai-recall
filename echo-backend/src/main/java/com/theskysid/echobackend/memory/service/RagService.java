package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.memory.dto.RagContextDTO;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private static final String ANSWER_SYSTEM =
            "You are an AI project assistant. Answer the user's question using ONLY the provided context. " +
            "If the answer is not in the context, clearly state that you do not know. " +
            "Keep the answer concise and professional.";

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    /**
     * Embed the query, fetch the top 5 most similar memory vectors for the given
     * channel only (superseded decisions demoted), combine their text, and ask
     * the LLM to synthesize a natural-language answer grounded in that context.
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

        String answer = generateAnswer(query, context);

        return RagContextDTO.builder()
                .channelId(channel)
                .query(query)
                .answer(answer)
                .context(context)
                .sourceIds(sourceIds)
                .build();
    }

    /**
     * Ask the LLM to answer the query grounded strictly in the retrieved context.
     * On any LLM failure (e.g. Groq rate limit), fall back to the raw context so
     * the UI never breaks.
     */
    public String generateAnswer(String query, String retrievedContext) {
        if (retrievedContext == null || retrievedContext.isBlank()) {
            return "I do not have any information about that yet.";
        }
        try {
            return chatLanguageModel.generate(List.of(
                    SystemMessage.from(ANSWER_SYSTEM),
                    UserMessage.from("Context:\n" + retrievedContext + "\n\nQuestion: " + query)
            )).content().text();
        } catch (Exception e) {
            logger.warn("Answer generation failed, falling back to raw context: {}", e.getMessage());
            return retrievedContext;
        }
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
