package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.memory.entity.MemoryStatus;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.entity.SourceType;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the conflict wiring: what each outcome does to the stored rows, and
 * that a failed classification is never mistaken for one. The LLM is stubbed,
 * so these pin the persistence behaviour, the strict reply contract and the
 * error path — not the quality of the classification prompt, which is an
 * unvalidated placeholder whose accuracy is measured separately.
 *
 * Sample content is invented for the test and matches no real project.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionServiceTest {

    private static final String OLD_DECISION = "We are going with Widgetron for the parts catalogue.";

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MemoryVectorRepository memoryVectorRepository;

    @InjectMocks
    private DecisionService decisionService;

    /** Replies handed to the model in order: extract, title, then classify. */
    private final Deque<String> replies = new ArrayDeque<>();

    private MemoryVector oldVector;

    @BeforeEach
    void setUp() {
        replies.clear();

        when(chatLanguageModel.generate(any(List.class))).thenAnswer(inv -> {
            String next = replies.isEmpty() ? "NO" : replies.poll();
            return Response.from(AiMessage.from(next));
        });

        // save() returns its argument and assigns an id, like Hibernate does.
        when(memoryVectorRepository.save(any(MemoryVector.class))).thenAnswer(inv -> {
            MemoryVector v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        oldVector = vector(OLD_DECISION);
        oldVector.setId(UUID.randomUUID());
        oldVector.setDecision(true);

        when(memoryVectorRepository.findTopDecisionsByChannel(anyLong(), anyString()))
                .thenReturn(List.of(oldVector));
    }

    private MemoryVector vector(String content) {
        return MemoryVector.builder()
                .channelId(7L)
                .content(content)
                .embedding(new float[]{0.1f, 0.2f})
                .sourceType(SourceType.MESSAGE)
                .sourceId(1L)
                .build();
    }

    /**
     * @param extract  reply to the "is this a decision" call
     * @param classify reply to the conflict classifier ("" to skip)
     */
    private MemoryVector ingest(String content, String extract, String classify) {
        replies.add(extract);
        if ("YES".equals(extract)) {
            replies.add("A title");     // consumed by generateTitle
            replies.add(classify);
        }
        MemoryVector v = vector(content);
        decisionService.processSupersession(v);
        return v;
    }

    // ── The five content categories ──────────────────────────────────────

    @Test
    void proposalLeavesTheOldDecisionAlone() {
        // A suggestion, not a decision — the extractor rejects it outright.
        ingest("Should we look at Gearbase instead of Widgetron?", "NO", "");

        assertEquals(MemoryStatus.CURRENT, oldVector.getStatus());
        assertNull(oldVector.getSupersedesId());
        verify(memoryVectorRepository, never()).save(any(MemoryVector.class));
    }

    @Test
    void discussionLeavesTheOldDecisionAlone() {
        // Reaches the classifier but is about a different question.
        ingest("We settled on quarterly invoicing for the parts catalogue.", "YES", "NONE");

        assertEquals(MemoryStatus.CURRENT, oldVector.getStatus());
        assertNull(oldVector.getSupersedesId());
        assertNull(oldVector.getConflictsWithId());
    }

    @Test
    void explicitReversalSupersedesTheOldDecision() {
        MemoryVector newer = ingest(
                "We are dropping Widgetron and moving the parts catalogue to Gearbase.",
                "YES", "SUPERSEDE");

        assertEquals(MemoryStatus.SUPERSEDED, oldVector.getStatus());
        assertEquals(newer.getId(), oldVector.getSupersedesId());
        assertEquals(MemoryStatus.CURRENT, newer.getStatus());
    }

    @Test
    void explicitFinalDecisionSupersedesTheOldDecision() {
        MemoryVector newer = ingest(
                "Final call: the parts catalogue runs on Gearbase.",
                "YES", "SUPERSEDE");

        assertEquals(MemoryStatus.SUPERSEDED, oldVector.getStatus());
        assertEquals(newer.getId(), oldVector.getSupersedesId());
    }

    @Test
    void genuineConflictMarksBothUnresolvedAndDemotesNeither() {
        MemoryVector newer = ingest(
                "Ops are already loading the parts catalogue into Gearbase this sprint.",
                "YES", "UNRESOLVED");

        assertEquals(MemoryStatus.UNRESOLVED, oldVector.getStatus());
        assertEquals(MemoryStatus.UNRESOLVED, newer.getStatus());
        assertEquals(newer.getId(), oldVector.getConflictsWithId());
        assertEquals(oldVector.getId(), newer.getConflictsWithId());

        // Neither side is superseded, so neither is demoted or filtered out:
        // both retrieval queries key off status = 'SUPERSEDED'.
        assertNull(oldVector.getSupersedesId());
        assertNull(newer.getSupersedesId());
    }

    // ── Parser and title fallback ────────────────────────────────────────

    // ── The strict reply contract ────────────────────────────────────────

    @Test
    void parserAcceptsTheThreeAllowedTokens() {
        assertEquals(DecisionService.ConflictResult.SUPERSEDE,
                decisionService.parseConflict("SUPERSEDE"));
        assertEquals(DecisionService.ConflictResult.UNRESOLVED,
                decisionService.parseConflict("UNRESOLVED"));
        assertEquals(DecisionService.ConflictResult.NO_CONFLICT,
                decisionService.parseConflict("NONE"));

        // Surrounding whitespace and case are the only leniency.
        assertEquals(DecisionService.ConflictResult.SUPERSEDE,
                decisionService.parseConflict("  supersede\n"));
    }

    @Test
    void parserRejectsAnythingThatIsNotOneOfTheThreeTokens() {
        // These two were SUPERSEDE / UNRESOLVED under the old lenient parser.
        // Prose is no longer a classification, however plausible it reads.
        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict("It replaces the old one."));
        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict("This is still UNRESOLVED, it may replace it later."));

        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict("SUPERSEDE."));
        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict("The answer is NONE"));
        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict(""));
        assertEquals(DecisionService.ConflictResult.LLM_ERROR,
                decisionService.parseConflict(null));
    }

    @Test
    void malformedReplyIsAnErrorNotNoConflict() {
        replies.add("I'd say the newer one probably wins here.");

        DecisionService.ConflictResult result =
                decisionService.classifyConflict("New thing", OLD_DECISION);

        assertEquals(DecisionService.ConflictResult.LLM_ERROR, result);
        assertNotEquals(DecisionService.ConflictResult.NO_CONFLICT, result);
    }

    @Test
    void modelExceptionIsAnErrorNotNoConflict() {
        when(chatLanguageModel.generate(any(List.class)))
                .thenThrow(new RuntimeException("rate_limit_exceeded"));

        DecisionService.ConflictResult result =
                decisionService.classifyConflict("New thing", OLD_DECISION);

        assertEquals(DecisionService.ConflictResult.LLM_ERROR, result);
        assertNotEquals(DecisionService.ConflictResult.NO_CONFLICT, result);
    }

    @Test
    void apiTimeoutIsAnErrorNotNoConflict() {
        when(chatLanguageModel.generate(any(List.class)))
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")));

        DecisionService.ConflictResult result =
                decisionService.classifyConflict("New thing", OLD_DECISION);

        assertEquals(DecisionService.ConflictResult.LLM_ERROR, result);
        assertNotEquals(DecisionService.ConflictResult.NO_CONFLICT, result);
    }

    @Test
    void classificationErrorsAreCountedSeparatelyFromRealOutcomes() {
        long before = decisionService.getClassificationErrorCount();

        replies.add("NONE");
        decisionService.classifyConflict("a", OLD_DECISION);
        replies.add("SUPERSEDE");
        decisionService.classifyConflict("b", OLD_DECISION);
        assertEquals(before, decisionService.getClassificationErrorCount(),
                "a successful classification must not count as an error");

        replies.add("Well, it depends.");           // malformed
        decisionService.classifyConflict("c", OLD_DECISION);
        assertEquals(before + 1, decisionService.getClassificationErrorCount());

        when(chatLanguageModel.generate(any(List.class)))
                .thenThrow(new RuntimeException("boom"));   // call failure
        decisionService.classifyConflict("d", OLD_DECISION);
        assertEquals(before + 2, decisionService.getClassificationErrorCount());
    }

    @Test
    void llmErrorLeavesBothRowsCompletelyUnchanged() {
        MemoryVector newer = ingest(
                "We are dropping Widgetron and moving the parts catalogue to Gearbase.",
                "YES", "I cannot determine that from the given text.");

        // Old row: exactly as it was before ingestion.
        assertEquals(MemoryStatus.CURRENT, oldVector.getStatus());
        assertNull(oldVector.getSupersedesId());
        assertNull(oldVector.getConflictsWithId());

        // New row: recorded as a decision, but carries no conflict verdict.
        assertEquals(MemoryStatus.CURRENT, newer.getStatus());
        assertNull(newer.getSupersedesId());
        assertNull(newer.getConflictsWithId());

        assertEquals(1, decisionService.getClassificationErrorCount());
    }

    @Test
    void titleFallsBackToTheFirstWordsWhenTheModelFails() {
        when(chatLanguageModel.generate(any(List.class)))
                .thenThrow(new RuntimeException("rate_limit_exceeded"));

        String title = decisionService.generateTitle(
                "We are going with Widgetron for the parts catalogue starting next quarter.");

        assertNotNull(title);
        assertEquals("We are going with Widgetron for the parts…", title);
    }
}
