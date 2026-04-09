package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "model_routes")
@IdClass(ModelRouteEntity.ModelRouteId.class)
public class ModelRouteEntity extends PanacheEntityBase {

    @Id
    @Column(name = "model_id", nullable = false)
    public String modelId;

    @Id
    @Column(name = "backend_id", nullable = false)
    public String backendId;

    @Column(name = "base_url", nullable = false)
    public String baseUrl;

    @Column(nullable = false)
    public int weight;

    @Column(nullable = false)
    public boolean active;

    @Column(nullable = false)
    public int version;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    public static class ModelRouteId implements Serializable {
        public String modelId;
        public String backendId;

        public ModelRouteId() {
        }

        public ModelRouteId(String modelId, String backendId) {
            this.modelId = modelId;
            this.backendId = backendId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ModelRouteId that)) {
                return false;
            }
            return Objects.equals(modelId, that.modelId) && Objects.equals(backendId, that.backendId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelId, backendId);
        }
    }
}
