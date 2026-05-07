package org.example.kalkulationsprogramm.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Speichert pro Frontend-User und Entity-Typ den Zeitpunkt des letzten Zugriffs
 * auf eine bestimmte Entity. Wird verwendet, um Listen-Ansichten geräteübergreifend
 * nach "zuletzt aufgerufen" (Stack-Reihenfolge) zu sortieren.
 */
@Getter
@Setter
@Entity
@Table(name = "entity_last_accessed")
public class EntityLastAccessed {

    @EmbeddedId
    private EntityLastAccessedId id;

    @Column(name = "zugegriffen_am", nullable = false)
    private LocalDateTime zugegriffenAm;

    public EntityLastAccessed() {
    }

    public EntityLastAccessed(Long userId, String entityType, Long entityId, LocalDateTime zugegriffenAm) {
        this.id = new EntityLastAccessedId(userId, entityType, entityId);
        this.zugegriffenAm = zugegriffenAm;
    }

    @Embeddable
    @Getter
    @Setter
    public static class EntityLastAccessedId implements Serializable {

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "entity_type", nullable = false, length = 64)
        private String entityType;

        @Column(name = "entity_id", nullable = false)
        private Long entityId;

        public EntityLastAccessedId() {
        }

        public EntityLastAccessedId(Long userId, String entityType, Long entityId) {
            this.userId = userId;
            this.entityType = entityType;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EntityLastAccessedId that)) return false;
            return Objects.equals(userId, that.userId)
                    && Objects.equals(entityType, that.entityType)
                    && Objects.equals(entityId, that.entityId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, entityType, entityId);
        }
    }
}
