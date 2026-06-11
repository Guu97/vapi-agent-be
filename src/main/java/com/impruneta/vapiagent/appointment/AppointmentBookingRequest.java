package com.impruneta.vapiagent.appointment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Incoming request body for booking a new appointment.
 * Validated at the controller boundary via @Valid.
 */
public record AppointmentBookingRequest(

    @NotBlank
    String citizenName,

    @NotBlank
    @Email
    String citizenEmail,

    @NotBlank
    String serviceTypeCode,

    @NotNull
    @Future
    LocalDate appointmentDate,

    @NotNull
    LocalTime appointmentTime,

    String notes
) {
}
