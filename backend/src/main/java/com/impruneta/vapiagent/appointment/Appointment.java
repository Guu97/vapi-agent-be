package com.impruneta.vapiagent.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A citizen appointment booked with a municipal service.
 *
 * The service_type_code column is stored as a plain String (denormalised FK reference)
 * rather than a @ManyToOne join. This keeps the entity simple and avoids eager-loading
 * the ServiceType on every appointment query. Referential integrity is enforced by the DB.
 *
 * Soft-delete pattern: rows are never physically deleted; deleted=true marks them as removed.
 * Business queries must always filter on deleted=false.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "citizen_name", nullable = false, length = 150)
    private String citizenName;

    @Column(name = "citizen_email", nullable = false, length = 255)
    private String citizenEmail;

    @Column(name = "service_type_code", nullable = false, length = 100)
    private String serviceTypeCode;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppointmentStatus status;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Appointment() {
        // Required by JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCitizenName() { return citizenName; }
    public void setCitizenName(String citizenName) { this.citizenName = citizenName; }

    public String getCitizenEmail() { return citizenEmail; }
    public void setCitizenEmail(String citizenEmail) { this.citizenEmail = citizenEmail; }

    public String getServiceTypeCode() { return serviceTypeCode; }
    public void setServiceTypeCode(String serviceTypeCode) { this.serviceTypeCode = serviceTypeCode; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
