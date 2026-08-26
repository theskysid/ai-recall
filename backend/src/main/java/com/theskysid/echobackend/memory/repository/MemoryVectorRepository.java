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
     * Retrieve the top 5 most similar *active* vectors for a channel — superseded
     * decisions are excluded by the WHERE clause and can never reach the prompt.
     *
     * Excluding rather than demoting holds even in a sparse channel: with fewer
     * than 5 active vectors it returns a short list rather than backfilling the
     * remaining slots with dead decisions.
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
