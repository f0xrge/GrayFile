package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "litellm_resources")
public class LiteLlmResourceEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "entity_type", nullable = false)
    public String entityType;

    @Column(name = "entity_id", nullable = false)
    public String entityId;

    @Column(name = "litellm_resource_type", nullable = false)
    public String liteLlmResourceType;

    @Column(name = "litellm_resource_id")
    public String liteLlmResourceId;

    @Column(name = "last_sync_status", nullable = false)
    public String lastSyncStatus;

    @Column(name = "last_sync_error")
    public String lastSyncError;

    @Column(name = "last_synced_at")
    public Instant lastSyncedAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
