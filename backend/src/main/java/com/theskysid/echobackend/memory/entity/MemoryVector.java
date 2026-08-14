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
    // DB default lets ddl-auto add the column to already-populated tables.
    @Builder.Default
    @Column(name = "is_decision", nullable = false, columnDefinition = "boolean not null default false")
    private boolean isDecision = false;

    // Short human label for the item, generated at ingestion. Nullable on
    // purpose: rows written before titles existed keep a null title and the UI
    // falls back to the content.
    @Column(name = "title")
    private String title;

    // What retrieval and the UI read. Kept in step with supersedesId:
    // status == SUPERSEDED iff supersedesId != null. The DB default lets
    // ddl-auto add the column to an already-populated table; existing
    // superseded rows are corrected by backfillStatuses() on startup.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false,
            columnDefinition = "varchar(20) not null default 'CURRENT'")
    private MemoryStatus status = MemoryStatus.CURRENT;

    // If set, the id of the newer decision that replaced this one. When present,
    // this vector is superseded and gets demoted in retrieval.
    @Column(name = "supersedes_id")
    private UUID supersedesId;

    // The other side of an unresolved clash. Set on both rows, pointing at each
    // other, when status == UNRESOLVED. Neither row is demoted.
    @Column(name = "conflicts_with_id")
    private UUID conflictsWithId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
