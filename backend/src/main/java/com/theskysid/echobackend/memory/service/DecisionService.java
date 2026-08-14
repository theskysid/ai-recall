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
import java.util.concurrent.atomic.AtomicLong;

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
    // Replace the semantic rules in the prompt below — the callers, the enum,
    // the persistence wiring and the strict one-token reply contract stay as
    // they are. parseConflict is deliberately exact; do not loosen it to cover
    // a prompt that talks back.
    // ─────────────────────────────────────────────────────────────────────
    private static final String CONFLICT_SYSTEM =
            "You compare a new statement against an older recorded decision. " +
            "Reply with exactly one of these tokens and nothing else: SUPERSEDE, UNRESOLVED, NONE. " +
            "No explanation, no punctuation, no other words — any other reply is discarded.\n" +
            "SUPERSEDE — the new statement is itself a settled decision that replaces or reverses the old one.\n" +
            "UNRESOLVED — the two are about the same question and disagree, but the new statement is a " +
            "proposal, a question, or an ongoing discussion rather than a settled decision.\n" +
            "NONE — they are about different questions, or they agree.";

    private static final String TITLE_SYSTEM =
            "Write a title for the decision below: at most 8 words, no quotes, no trailing period. " +
            "Reply with the title only.";

    /** Marker for grepping classification failures out of the logs. */
    static final String LLM_ERROR_MARKER = "CONFLICT_CLASSIFICATION_LLM_ERROR";

    /** Marker for grepping extraction failures out of the logs — the earlier stage. */
    static final String EXTRACT_ERROR_MARKER = "DECISION_EXTRACTION_LLM_ERROR";

    /** Outcome of asking whether one piece of text records a decision. */
    public enum ExtractionResult {
        /** The text states a settled decision; it enters the supersession pipeline. */
        DECISION,
        /** The text is ordinary chatter; stored as a plain memory. */
        NOT_A_DECISION,
        /**
         * The extractor never produced a usable answer — the call failed, timed
         * out, or the reply was not YES or NO. NOT a verdict: the vector is stored
         * as an ordinary memory (same as NOT_A_DECISION) and counted separately, so
         * a throttled model is never read as "nobody decided anything here".
         */
        LLM_ERROR
    }

    /** Outcome of comparing a new decision against one older decision. */
    public enum ConflictResult {
        /** The new decision replaces the old one; the old one becomes SUPERSEDED. */
        SUPERSEDE,
        /** Same question, genuine disagreement, nothing settled it; both become UNRESOLVED. */
        UNRESOLVED,
        /** Unrelated or in agreement; nothing changes. */
        NO_CONFLICT,
        /**
         * The classifier never produced a usable answer — the call failed, timed
         * out, or the reply was not one of the three allowed tokens. NOT a
         * classification: the pair is left untouched and counted separately, so a
         * throttled model can never be read as "these two don't conflict".
         */
        LLM_ERROR
    }

    /**
     * Failures since startup, kept apart from the real outcomes and from each
     * other — the two stages fail for different reasons and drop-off has to be
     * attributable to the right one.
     * TODO: in-process counters, per instance and reset on restart — wire a
     * Micrometer MeterRegistry here if these ever need to be scraped.
     */
    private final AtomicLong classificationErrors = new AtomicLong();
    private final AtomicLong extractionErrors = new AtomicLong();

    /** Number of pairs the classifier failed on since startup. */
    public long getClassificationErrorCount() {
        return classificationErrors.get();
    }

    /** Number of texts the extractor failed on since startup. */
    public long getExtractionErrorCount() {
        return extractionErrors.get();
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
     * Returns LLM_ERROR — never NOT_A_DECISION — when the call fails or the reply
     * isn't YES or NO, so a throttled model is never mistaken for a text that
     * simply held no decision. Ingestion never breaks either way: both non-DECISION
     * outcomes leave the vector to be stored as an ordinary memory.
     */
    public ExtractionResult extractDecision(String text) {
        if (text == null || text.isBlank()) {
            return ExtractionResult.NOT_A_DECISION;
        }
        ExtractionResult result;
        try {
            String answer = chatLanguageModel.generate(List.of(
                    SystemMessage.from(EXTRACT_SYSTEM),
                    UserMessage.from(text)
            )).content().text();
            result = parseExtraction(answer);
            if (result == ExtractionResult.LLM_ERROR) {
                logger.warn("{} unusable extractor reply: {}", EXTRACT_ERROR_MARKER, abbreviate(answer));
            }
        } catch (Exception e) {
            // Message only — an exception from the HTTP client can carry the
            // request headers, and those hold the API key.
            logger.warn("{} extractor call failed: {}", EXTRACT_ERROR_MARKER, e.getMessage());
            result = ExtractionResult.LLM_ERROR;
        }
        if (result == ExtractionResult.LLM_ERROR) {
            extractionErrors.incrementAndGet();
        }
        return result;
    }

    /**
     * Read the extractor's reply. EXTRACT_SYSTEM asks for one word, so exactly
     * YES or NO (modulo surrounding whitespace and case) — anything else is
     * LLM_ERROR rather than a guess.
     */
    ExtractionResult parseExtraction(String answer) {
        if (answer == null) {
            return ExtractionResult.LLM_ERROR;
        }
        return switch (answer.trim().toUpperCase()) {
            case "YES" -> ExtractionResult.DECISION;
            case "NO" -> ExtractionResult.NOT_A_DECISION;
            default -> ExtractionResult.LLM_ERROR;
        };
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

        // Anything that isn't a confirmed decision — including a failed
        // extraction — leaves the vector as a plain memory for the caller to
        // save: no flag, no title, no supersession.
        ExtractionResult extraction = extractDecision(newVector.getContent());
        newVector.setDecision(extraction == ExtractionResult.DECISION);
        if (extraction != ExtractionResult.DECISION) {
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
                    // Already logged and counted in classifyConflict. Both rows
                    // stay exactly as they were — an unclassified pair is not
                    // evidence of anything.
                    case LLM_ERROR -> { /* nothing to do */ }
                }
            }
        } catch (Exception e) {
            logger.warn("Supersession processing failed: {}", e.getMessage());
        }
    }

    /**
     * Classify a new decision against one older decision. See CONFLICT_SYSTEM —
     * the prompt is a placeholder awaiting the real, hand-tuned rules.
     * Returns LLM_ERROR — never NO_CONFLICT — when the call fails or the reply
     * isn't one of the three allowed tokens, so a throttled model is never
     * mistaken for a genuine "these don't conflict".
     */
    ConflictResult classifyConflict(String newDecision, String oldDecision) {
        ConflictResult result;
        try {
            String answer = chatLanguageModel.generate(List.of(
                    SystemMessage.from(CONFLICT_SYSTEM),
                    UserMessage.from("Old decision: \"" + oldDecision + "\"\n"
                            + "New statement: \"" + newDecision + "\"\n"
                            + "Answer with one token: SUPERSEDE, UNRESOLVED or NONE.")
            )).content().text();
            result = parseConflict(answer);
            if (result == ConflictResult.LLM_ERROR) {
                logger.warn("{} unusable classifier reply: {}", LLM_ERROR_MARKER, abbreviate(answer));
            }
        } catch (Exception e) {
            // Message only — an exception from the HTTP client can carry the
            // request headers, and those hold the API key.
            logger.warn("{} classifier call failed: {}", LLM_ERROR_MARKER, e.getMessage());
            result = ConflictResult.LLM_ERROR;
        }
        if (result == ConflictResult.LLM_ERROR) {
            classificationErrors.incrementAndGet();
        }
        return result;
    }

    /**
     * Read the classifier's reply. Exactly one of the three allowed tokens,
     * modulo surrounding whitespace and case — anything else (prose, an
     * explanation, an empty reply, a token buried in a sentence) is LLM_ERROR.
     */
    ConflictResult parseConflict(String answer) {
        if (answer == null) {
            return ConflictResult.LLM_ERROR;
        }
        return switch (answer.trim().toUpperCase()) {
            case "SUPERSEDE" -> ConflictResult.SUPERSEDE;
            case "UNRESOLVED" -> ConflictResult.UNRESOLVED;
            case "NONE" -> ConflictResult.NO_CONFLICT;
            default -> ConflictResult.LLM_ERROR;
        };
    }

    private String abbreviate(String reply) {
        if (reply == null) {
            return "<null>";
        }
        String r = reply.trim().replaceAll("\\s+", " ");
        return r.length() <= 120 ? r : r.substring(0, 120) + "…";
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

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
