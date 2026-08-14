package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.memory.entity.MemoryStatus;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.PostConstruct;
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

    // ─────────────────────────────────────────────────────────────────────
    // PLACEHOLDER — STARTING POINT ONLY, NOT THE RESEARCH CONTRIBUTION.
    //
    // This prompt is wiring scaffolding so the three-way path compiles and can
    // be exercised. It has NOT been tuned and its decision boundary has not
    // been validated: telling "we're switching to Oracle" from "let's discuss
    // Oracle tomorrow" reliably is the actual contribution and must be written
    // and tuned by hand before any of this is presented as a result.
    //
    // Replace the prompt below (and, if needed, parseConflict) — the callers,
    // the enum and the persistence wiring stay as they are.
    // ─────────────────────────────────────────────────────────────────────
    private static final String CONFLICT_SYSTEM =
            "You compare a new statement against an older recorded decision. " +
            "Reply with exactly one word, one of: SUPERSEDE, UNRESOLVED, NONE.\n" +
            "SUPERSEDE — the new statement is itself a settled decision that replaces or reverses the old one.\n" +
            "UNRESOLVED — the two are about the same question and disagree, but the new statement is a " +
            "proposal, a question, or an ongoing discussion rather than a settled decision.\n" +
            "NONE — they are about different questions, or they agree.";

    private static final String TITLE_SYSTEM =
            "Write a title for the decision below: at most 8 words, no quotes, no trailing period. " +
            "Reply with the title only.";

    /** Outcome of comparing a new decision against one older decision. */
    public enum ConflictResult {
        /** The new decision replaces the old one; the old one becomes SUPERSEDED. */
        SUPERSEDE,
        /** Same question, genuine disagreement, nothing settled it; both become UNRESOLVED. */
        UNRESOLVED,
        /** Unrelated or in agreement; nothing changes. */
        NO_CONFLICT
    }

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    /**
     * `status` was added after this table already held rows, and ddl-auto gives
     * every existing row the column default ('CURRENT') — including rows that
     * were already superseded. Correct them once at startup so status and
     * supersedes_id never disagree. No-op on a fresh database.
     */
    @PostConstruct
    void backfillStatuses() {
        try {
            int fixed = memoryVectorRepository.backfillStatuses();
            if (fixed > 0) {
                logger.info("Backfilled status=SUPERSEDED on {} existing memory vectors", fixed);
            }
        } catch (Exception e) {
            logger.warn("Status backfill failed: {}", e.getMessage());
        }
    }

    /**
     * Ask the LLM whether the text contains a final project/technical decision.
     * Returns false on any error so ingestion never breaks.
     */
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

        if (newVector.getTitle() == null) {
            newVector.setTitle(generateTitle(newVector.getContent()));
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
                switch (classifyConflict(saved.getContent(), old.getContent())) {
                    case SUPERSEDE -> {
                        old.setSupersedesId(saved.getId());
                        old.setStatus(MemoryStatus.SUPERSEDED);
                        memoryVectorRepository.save(old);
                    }
                    case UNRESOLVED -> {
                        // Both sides stay retrievable — neither replaced the other.
                        // Only one clash is recorded per row: with several the last
                        // one seen wins the pointer, while the status still says
                        // "contested". A join table would be the fix if that matters.
                        old.setStatus(MemoryStatus.UNRESOLVED);
                        old.setConflictsWithId(saved.getId());
                        memoryVectorRepository.save(old);

                        saved.setStatus(MemoryStatus.UNRESOLVED);
                        saved.setConflictsWithId(old.getId());
                        memoryVectorRepository.save(saved);
                    }
                    case NO_CONFLICT -> { /* nothing to do */ }
                }
            }
        } catch (Exception e) {
            logger.warn("Supersession processing failed: {}", e.getMessage());
        }
    }

    /**
     * Classify a new decision against one older decision. See CONFLICT_SYSTEM —
     * the prompt is a placeholder awaiting the real, hand-tuned rules.
     * Returns NO_CONFLICT on any error so ingestion never breaks, which means a
     * throttled LLM looks exactly like "unrelated" (same caveat as extractDecision).
     */
    ConflictResult classifyConflict(String newDecision, String oldDecision) {
        try {
            String answer = chatLanguageModel.generate(List.of(
                    SystemMessage.from(CONFLICT_SYSTEM),
                    UserMessage.from("Old decision: \"" + oldDecision + "\"\n"
                            + "New statement: \"" + newDecision + "\"\n"
                            + "Answer SUPERSEDE, UNRESOLVED or NONE.")
            )).content().text();
            return parseConflict(answer);
        } catch (Exception e) {
            logger.warn("Conflict classification failed: {}", e.getMessage());
            return ConflictResult.NO_CONFLICT;
        }
    }

    /**
     * Read the classifier's reply. Small models rarely answer with a bare token,
     * so UNRESOLVED is checked first — a reply that mentions both an unresolved
     * clash and a replacement is a clash, not a replacement.
     */
    ConflictResult parseConflict(String answer) {
        if (answer == null) {
            return ConflictResult.NO_CONFLICT;
        }
        String a = answer.trim().toUpperCase();
        if (a.contains("UNRESOLVED") || a.contains("UNSETTLED") || a.contains("PROPOS")
                || a.contains("DISCUSS")) {
            return ConflictResult.UNRESOLVED;
        }
        if (a.startsWith("NONE") || a.startsWith("NO")) {
            return ConflictResult.NO_CONFLICT;
        }
        return isAffirmative(a) ? ConflictResult.SUPERSEDE : ConflictResult.NO_CONFLICT;
    }

    /**
     * Short label for the decisions list. Falls back to the first few words of
     * the content when the LLM is unavailable, so a title always exists.
     */
    String generateTitle(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            String title = chatLanguageModel.generate(List.of(
                    SystemMessage.from(TITLE_SYSTEM),
                    UserMessage.from(content)
            )).content().text();
            title = title == null ? "" : title.trim().replaceAll("^\"|\"$", "");
            if (!title.isBlank() && title.length() <= 120) {
                return title;
            }
        } catch (Exception e) {
            logger.warn("Title generation failed: {}", e.getMessage());
        }
        return truncateWords(content, 8);
    }

    private String truncateWords(String content, int limit) {
        String[] words = content.trim().split("\\s+");
        if (words.length <= limit) {
            return String.join(" ", words);
        }
        return String.join(" ", List.of(words).subList(0, limit)) + "…";
    }

    private boolean isYes(String answer) {
        return answer != null && answer.trim().toUpperCase().startsWith("YES");
    }

    /**
     * Lenient affirmative check — small models often answer with a synonym
     * ("Contradict.", "Replaces") instead of a bare YES.
     */
    private boolean isAffirmative(String answer) {
        if (answer == null) {
            return false;
        }
        String a = answer.trim().toUpperCase();
        if (a.startsWith("NO") || a.contains("INDEPENDENT") || a.contains("UNRELATED") || a.contains("NOT ")) {
            return false;
        }
        return a.startsWith("YES") || a.contains("YES") || a.contains("SUPERSED")
                || a.contains("REPLACE") || a.contains("CONTRADICT") || a.contains("REVERSE");
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
