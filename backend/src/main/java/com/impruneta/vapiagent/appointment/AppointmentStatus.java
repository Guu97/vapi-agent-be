package com.impruneta.vapiagent.appointment;

/**
 * Allowed values for appointment.status in the database.
 * Mirrors the CHECK constraint: status IN ('BOOKED', 'CANCELLED').
 */
public enum AppointmentStatus {
    BOOKED,
    CANCELLED
}
