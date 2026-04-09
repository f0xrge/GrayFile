package io.grayfile.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@ApplicationScoped
public class AuditExportStateRepository implements PanacheRepositoryBase<AuditExportStateRepository.AuditExportStateEntity, Short> {

    public AuditExportStateEntity getOrCreate() {
        return findByIdOptional((short) 1).orElseGet(() -> {
            AuditExportStateEntity entity = new AuditExportStateEntity();
            entity.id = 1;
            entity.lastExportedEventId = 0;
            entity.updatedAt = Instant.now();
            persist(entity);
            return entity;
        });
    }

    @Entity
    @Table(name = "audit_export_state")
    public static class AuditExportStateEntity {
        @Id
        public short id;

        @Column(name = "last_exported_event_id", nullable = false)
        public long lastExportedEventId;

        @Column(name = "updated_at", nullable = false)
        public Instant updatedAt;
    }
}
