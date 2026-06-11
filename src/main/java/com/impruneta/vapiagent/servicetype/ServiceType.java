package com.impruneta.vapiagent.servicetype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Lookup table for service types offered by the municipality.
 *
 * Codes used in this project: ANAGRAFE, TRIBUTI, URP, SERVIZI_SOCIALI.
 * Each code maps directly to the service_type.code PK in the database
 * and is referenced as a foreign key from the appointment table.
 */
@Entity
@Table(name = "service_type")
public class ServiceType {

    @Id
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceType() {
        // Required by JPA
    }

    public ServiceType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public String getCode() { return code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
