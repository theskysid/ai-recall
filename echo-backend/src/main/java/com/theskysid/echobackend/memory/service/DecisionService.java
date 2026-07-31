package com.theskysid.echobackend.memory.service;

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

@Service
public class DecisionService {

    private static final Logger logger = LoggerFactory.getLogger(DecisionService.class);

    private static final String EXTRACT_SYSTEM =
            "You classify text. Reply with exactly one word: YES or NO. " +
            "Reply YES only if the text states a final, concrete project or technical decision. Otherwise NO.";

    private static final String SUPERSEDE_SYSTEM = "Reply with exactly one word: YES or NO.";

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    /**
     * Ask the LLM whether the text contains a final project/technical decision.
     * Returns false on any error so ingestion never breaks.
     */
    public boolean isDecision(String text) {
        return extractDecision(text);
    }

    public boolean extractDecision(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            String answer = chatLanguageModel.generate(List.of(
                    SystemMessage.from(EXTRACT_SYSTEM),
                    UserMessage.from(text)
            )).content().text();
            return isYes(answer);
        } catch (Exception e) {
            logger.warn("Decision extraction failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * If the new vector is a decision, flag it and supersede any older active
     * decision in the same channel that it replaces/contradicts. Runs synchronously
     * inside the caller's @Async ingestion flow.
     */
    public void processSupersession(MemoryVector newVector) {
        if (newVector == null || newVector.getContent() == null) {
            return;
        }

        boolean decision = extractDecision(newVector.getContent());
        newVector.setDecision(decision);
        if (!decision) {
            return;
        }

        try {
            // Persist now so the new decision has an id to link older decisions to.
            MemoryVector saved = memoryVectorRepository.save(newVector);

            float[] embedding = saved.getEmbedding() != null
                    ? saved.getEmbedding()
                    : embeddingService.embed(saved.getContent());

            List<MemoryVector> oldDecisions =
                    memoryVectorRepository.findTopDecisionsByChannel(saved.getChannelId(), toVectorLiteral(embedding));

            for (MemoryVector old : oldDecisions) {
                if (old.getId().equals(saved.getId())) {
                    continue;
                }
                if (replaces(saved.getContent(), old.getContent())) {
                    old.setSupersedesId(saved.getId());
                    memoryVectorRepository.save(old);
                }
            }
        } catch (Exception e) {
            logger.warn("Supersession processing failed: {}", e.getMessage());
        }
    }

    private boolean replaces(String newDecision, String oldDecision) {
        try {
            String answer = chatLanguageModel.generate(List.of(
                    SystemMessage.from(SUPERSEDE_SYSTEM),
                    UserMessage.from("New decision: \"" + newDecision + "\"\n"
                            + "Old decision: \"" + oldDecision + "\"\n"
                            + "Does the new decision replace or contradict the old one?")
            )).content().text();
            return isYes(answer);
        } catch (Exception e) {
            logger.warn("Supersession check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isYes(String answer) {
        return answer != null && answer.trim().toUpperCase().startsWith("YES");
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
