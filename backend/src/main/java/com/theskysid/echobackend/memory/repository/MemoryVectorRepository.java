package com.theskysid.echobackend.memory.repository;

import com.theskysid.echobackend.memory.entity.MemoryVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface MemoryVectorRepository extends JpaRepository<MemoryVector, UUID> {

    /**
     * Retrieve the top 5 most similar vectors for a single channel using
     * pgvector's cosine distance operator (<=>). Strictly filters by channel_id
     * BEFORE ordering so results can never leak across channels.
     *
     * Ordering is by a calculated score = cosine distance + supersession penalty.
     * Cosine distance (<=>) is in [0, 2], where smaller = more similar. Any vector
     * with status = 'SUPERSEDED' (it was explicitly replaced by a newer decision)
     * has a large penalty (10) added to its distance, pushing it to the bottom of
     * the list while still allowing it to be returned as a last resort.
     *
     * The penalty keys off status, not supersedes_id, so that an UNRESOLVED item
     * is left alone: it conflicts with something but nothing replaced it, and
     * demoting it would quietly reinstate "newest wins".
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "WHERE channel_id = :channelId " +
            "ORDER BY (embedding <=> CAST(:embedding AS vector)) " +
            "+ (CASE WHEN status = 'SUPERSEDED' THEN 10 ELSE 0 END) ASC " +
            "LIMIT 5", nativeQuery = true)
    List<MemoryVector> findTop5ByChannelAndSimilarity(@Param("channelId") Long channelId,
                                                      @Param("embedding") String embedding);

    /**
     * Retrieve the top 5 most similar vectors for a channel, ignoring supersession
     * entirely — plain cosine similarity, the way a stock RAG pipeline retrieves.
     *
     * This is the control arm for the staleness evaluation: identical embeddings,
     * prompt and model to the other two, differing only in that a decision the
     * team already reversed can win a slot on similarity alone.
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "WHERE channel_id = :channelId " +
            "ORDER BY embedding <=> CAST(:embedding AS vector) ASC " +
            "LIMIT 5", nativeQuery = true)
    List<MemoryVector> findTop5Baseline(@Param("channelId") Long channelId,
                                        @Param("embedding") String embedding);

    /**
     * Retrieve the top 5 most similar *active* vectors for a channel — superseded
     * decisions are excluded by the WHERE clause and can never reach the prompt.
     *
     * Unlike the demoting query above, this holds even in a sparse channel: with
     * fewer than 5 active vectors it returns a short list rather than backfilling
     * the remaining slots with dead decisions.
     *
     * Only SUPERSEDED is excluded. UNRESOLVED items still retrieve normally.
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "WHERE channel_id = :channelId AND status <> 'SUPERSEDED' " +
            "ORDER BY embedding <=> CAST(:embedding AS vector) ASC " +
            "LIMIT 5", nativeQuery = true)
    List<MemoryVector> findTop5ActiveOnly(@Param("channelId") Long channelId,
                                          @Param("embedding") String embedding);

    /**
     * Number of vectors stored for a channel — lets an ingestion caller wait for
     * the @Async embed/extract pipeline to drain before querying.
     */
    long countByChannelId(Long channelId);

    /**
     * Retrieve the top still-standing decisions most similar to the given
     * embedding, scoped to one channel. Only returns vectors that are decisions
     * and have not already been superseded. Used to find older decisions a new
     * decision might replace or clash with — an UNRESOLVED item is included,
     * since a later decision can still settle it.
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "WHERE channel_id = :channelId AND is_decision = true AND status <> 'SUPERSEDED' " +
            "ORDER BY embedding <=> CAST(:embedding AS vector) ASC " +
            "LIMIT 3", nativeQuery = true)
    List<MemoryVector> findTopDecisionsByChannel(@Param("channelId") Long channelId,
                                                 @Param("embedding") String embedding);

    /**
     * All decisions for a channel (both active and superseded), newest first —
     * for the decision timeline/history UI.
     */
    @Query("SELECT m FROM MemoryVector m WHERE m.channelId = :channelId AND m.isDecision = true " +
            "ORDER BY m.createdAt DESC")
    List<MemoryVector> findDecisionsByChannel(@Param("channelId") Long channelId);

    /**
     * Bring `status` in line with `supersedes_id` for rows written before the
     * status column existed. `ddl-auto` gives every existing row the column
     * default ('CURRENT'), which would silently un-supersede decisions that were
     * already superseded — and un-demote them in retrieval. Idempotent: after the
     * first run it matches nothing. Called once at startup.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE memory_vectors SET status = 'SUPERSEDED' " +
            "WHERE supersedes_id IS NOT NULL AND status <> 'SUPERSEDED'", nativeQuery = true)
    int backfillStatuses();
}
