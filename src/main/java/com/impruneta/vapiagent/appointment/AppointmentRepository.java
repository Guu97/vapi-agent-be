package com.impruneta.vapiagent.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByCitizenEmailAndDeletedFalse(String citizenEmail);

    List<Appointment> findByServiceTypeCodeAndDeletedFalse(String serviceTypeCode);

    /**
     * Partial case-insensitive name search.
     * Used when the citizen identifies themselves by name over the phone.
     */
    List<Appointment> findByCitizenNameContainingIgnoreCaseAndDeletedFalse(String citizenName);

    /**
     * Slot availability check: a slot is taken when a BOOKED non-deleted appointment
     * already exists for the exact serviceTypeCode + date + time combination.
     */
    boolean existsByServiceTypeCodeAndAppointmentDateAndAppointmentTimeAndStatusAndDeletedFalse(
        String serviceTypeCode,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        AppointmentStatus status
    );

    /**
     * Voice-friendly cancel lookup: citizen provides email + date + service
     * instead of the internal UUID they wouldn't know.
     */
    List<Appointment> findByCitizenEmailAndAppointmentDateAndServiceTypeCodeAndDeletedFalse(
        String citizenEmail,
        LocalDate appointmentDate,
        String serviceTypeCode
    );
}
