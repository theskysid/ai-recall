package com.theskysid.echobackend.memory.repository;

import com.theskysid.echobackend.memory.entity.MemoryVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemoryVectorRepository extends JpaRepository<MemoryVector, UUID> {

    /**
     * Nearest-neighbour similarity search using pgvector's L2 distance operator
     * (<->). Returns the closest stored vectors to the given query embedding,
     * ordered nearest first. The query embedding must be passed as a pgvector
     * literal string, e.g. "[0.1,0.2,...]".
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "ORDER BY embedding <-> CAST(:queryVector AS vector) ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<MemoryVector> findNearest(@Param("queryVector") String queryVector,
                                   @Param("limit") int limit);

    /**
     * Retrieve the top 5 most similar vectors for a single channel using
     * pgvector's cosine distance operator (<=>). Strictly filters by channel_id
     * BEFORE ordering so results can never leak across channels.
     *
     * Ordering is by a calculated score = cosine distance + supersession penalty.
     * Cosine distance (<=>) is in [0, 2], where smaller = more similar. Any vector
     * whose supersedes_id IS NOT NULL (it was explicitly replaced by a newer
     * decision) has a large penalty (10) added to its distance, pushing it to the
     * bottom of the list while still allowing it to be returned as a last resort.
     */
    @Query(value = "SELECT * FROM memory_vectors " +
            "WHERE channel_id = :channelId " +
            "ORDER BY (embedding <=> CAST(:embedding AS vector)) " +
            "+ (CASE WHEN supersedes_id IS NOT NULL THEN 10 ELSE 0 END) ASC " +
            "LIMIT 5", nativeQuery = true)
    List<MemoryVector> findTop5ByChannelAndSimilarity(@Param("channelId") Long channelId,
                                                      @Param("embedding") String embedding);
}
