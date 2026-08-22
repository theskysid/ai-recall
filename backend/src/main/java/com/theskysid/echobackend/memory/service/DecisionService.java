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
    // The decision boundary this whole feature rests on: separating a settled
    // replacement ("we've decided to switch to Oracle") from a proposal that
    // merely challenges the old decision ("let's switch to Oracle") from talk
    // that changes nothing ("Oracle might be faster").
    //
    // Small models default to treating the newer statement as the winner, so
    // the rules say outright that recency is not evidence. The examples carry
    // more weight than the definitions — keep them when editing the wording.
    //
    // parseConflict is deliberately exact; do not loosen it to cover a prompt
    // that talks back. Fix the prompt instead.
    // ─────────────────────────────────────────────────────────────────────
    private static final String CONFLICT_SYSTEM = """
            You compare a new statement against an older recorded decision and determine the relationship between them.
            
            Reply with exactly one of these tokens and nothing else: NONE, SUPERSEDE, UNRESOLVED.
            No explanation, no punctuation, no markdown, no other words — any other reply is discarded.
            
            Step 1 — Same decision/topic gate:
            First determine whether the new statement concerns the same underlying decision, question, choice, or subject as the existing decision.
            If it concerns a different decision or unrelated subject, reply NONE. Do NOT proceed to the supersession test.
            This gate has priority over all other rules. A new statement containing strong decision or reversal language must still be NONE if it concerns a different decision.
            
            Step 2 — Relationship to the existing decision:
            Only if the statements concern the same underlying decision, use the following rules:
            
            - SUPERSEDE: clear settled replacement/reversal. The new statement clearly establishes a settled replacement of the old decision: the team has decided, agreed, approved, or already moved.
            - UNRESOLVED: meaningful challenge, proposal, uncertainty, or reopening without a settled replacement. A conditional or future change that has not happened yet is UNRESOLVED.
            - NONE: no meaningful conflict or change. A question, suggestion, comparison, brainstorming idea, or a restatement of the old decision is not itself a replacement.
            
            Important distinction:
            Do not classify a statement as SUPERSEDE merely because it contains a decision, contains "we decided", contains "switch", contains "instead", contains reversal language, is newer, or uses similar vocabulary to the existing decision. It must first be about the same underlying decision.
            Repeating or confirming the same decision is not supersession.
            
            Examples (Unrelated Subjects -> NONE):
            Old: "We decided to use MongoDB for the database." New: "We've decided to use Loki for observability." -> NONE
            Old: "We decided to use PostgreSQL for the database." New: "We've decided to switch the frontend from React to Vue." -> NONE
            
            Examples (Restatement -> NONE):
            Old: "We decided to use PostgreSQL." New: "The team has decided to use PostgreSQL." -> NONE
            Old: "We decided to use PostgreSQL." New: "We're continuing with PostgreSQL." -> NONE
            Old: "We decided to use PostgreSQL." New: "We're still using PostgreSQL." -> NONE
            Old: "We decided to use PostgreSQL." New: "PostgreSQL is currently used by the application." -> NONE
            
            Examples (Unresolved Challenge -> UNRESOLVED):
            Old: "We decided to use PostgreSQL." New: "I think we should switch to Oracle." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "Let's switch to Oracle." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "We're probably switching to Oracle." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "I'm not sure PostgreSQL is still the right choice." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "We should discuss whether PostgreSQL is still the right choice." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "We haven't decided yet; Oracle and PostgreSQL are both options." -> UNRESOLVED
            Old: "We decided to use PostgreSQL." New: "If performance doesn't improve, we'll switch to Oracle." -> UNRESOLVED
            
            Examples (Settled Replacement -> SUPERSEDE):
            Old: "We decided to use PostgreSQL." New: "We've decided to use Oracle." -> SUPERSEDE
            Old: "We decided to use PostgreSQL." New: "We've decided to switch to Oracle." -> SUPERSEDE
            Old: "We decided to use PostgreSQL." New: "We've agreed to use Oracle instead." -> SUPERSEDE
            Old: "We decided to use PostgreSQL." New: "We're now using Oracle." -> SUPERSEDE
            Old: "We decided to use PostgreSQL." New: "Actually, we've changed the decision. We're using Oracle." -> SUPERSEDE
            Old: "We decided to use PostgreSQL." New: "The team has approved Oracle as the replacement for PostgreSQL." -> SUPERSEDE
            
            Rules:
            1. Recency alone must NEVER cause SUPERSEDE. That one statement is newer is not evidence by itself.
            2. A proposal is not a settled decision.
            3. A question is not a decision.
            4. Brainstorming is not a decision.
            5. A possibility or hypothetical future change is not a settled replacement.
            6. Use SUPERSEDE when the new statement clearly indicates the team or project has made a new decision.
            7. Use UNRESOLVED when it indicates meaningful disagreement or possible change but the matter is unsettled.
            8. Use NONE when there is no meaningful conflict or replacement relationship, or when they concern different subjects.
            9. Do not infer a decision that is not expressed or reasonably implied by the text.
            10. Ignore superficial wording differences when the underlying decision is clearly the same.
            11. If the new statement confirms or repeats the old decision, reply NONE.
            
            Your entire response is one token: NONE, SUPERSEDE or UNRESOLVED.""";

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
                    memoryVectorRepository.findTopDecisionsByChannel(saved.getChannelId(), embeddingService.toVectorLiteral(embedding));

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
     * Classify a new decision against one older decision. See CONFLICT_SYSTEM for
     * the boundary between a settled replacement and a mere proposal.
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
     *
     * Deliberately not a three-way result like the two stages above: a title is
     * cosmetic, the fallback invents nothing (it is the content's own opening
     * words), and no measurement reads it. Both fallback paths log, so a run of
     * truncated titles is diagnosable, but nothing counts them.
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
            logger.warn("Unusable title reply, falling back to content: {}", abbreviate(title));
        } catch (Exception e) {
            logger.warn("Title generation failed, falling back to content: {}", e.getMessage());
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

}
