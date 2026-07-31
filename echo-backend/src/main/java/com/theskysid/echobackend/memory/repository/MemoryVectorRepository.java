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
}
