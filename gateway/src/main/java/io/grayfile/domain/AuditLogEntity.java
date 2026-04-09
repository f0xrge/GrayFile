package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    public Long eventId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "actor", nullable = false)
    public String actor;

    @Column(name = "entity_type", nullable = false)
    public String entityType;

    @Column(name = "entity_id", nullable = false)
    public String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    public String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "prev_hash")
    public String prevHash;

    @Column(name = "event_hash", nullable = false)
    public String eventHash;

    @Column(name = "signature", nullable = false)
    public String signature;
}
