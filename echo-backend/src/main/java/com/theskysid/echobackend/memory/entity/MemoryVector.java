package com.theskysid.echobackend.memory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "memory_vectors")
public class MemoryVector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Stored as a pgvector column. all-MiniLM-L6-v2 outputs 384 dimensions.
    @Type(VectorType.class)
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    // Whether this vector represents an extracted "decision".
    @Builder.Default
    @Column(name = "is_decision", nullable = false)
    private boolean isDecision = false;

    // If set, the id of the newer decision that replaced this one. When present,
    // this vector is superseded and gets demoted in retrieval.
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
