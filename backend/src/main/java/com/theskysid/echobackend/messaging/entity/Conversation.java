package com.theskysid.echobackend.messaging.entity;

import com.theskysid.echobackend.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"participant_one_id", "participant_two_id"})
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_one_id", nullable = false)
    private User participantOne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_two_id", nullable = false)
    private User participantTwo;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_policy", nullable = false)
    private RetentionPolicy retentionPolicy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.retentionPolicy == null) {
            this.retentionPolicy = RetentionPolicy.ONE_DAY;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if a user is a participant in this conversation.
     */
    public boolean hasParticipant(User user) {
        return participantOne.getId().equals(user.getId())
                || participantTwo.getId().equals(user.getId());
    }

    /**
     * Get the other participant in the conversation.
     */
    public User getOtherParticipant(User user) {
        return participantOne.getId().equals(user.getId())
                ? participantTwo
                : participantOne;
    }
}
