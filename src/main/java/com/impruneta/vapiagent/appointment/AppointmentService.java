package com.impruneta.vapiagent.appointment;

import com.impruneta.vapiagent.servicetype.ServiceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles business logic for citizen appointments.
 *
 * Responsibilities:
 *  - Validate that the requested service type exists before booking
 *  - Enforce state transitions (cannot cancel an already-cancelled appointment)
 *  - Apply soft-delete semantics: deleted=true means logically removed
 *
 * Out of scope for v1:
 *  - Slot availability / conflict detection (would require a booking calendar)
 *  - Email notifications (would require a mail service)
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               ServiceTypeRepository serviceTypeRepository) {
        this.appointmentRepository = appointmentRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    /**
     * Books a new appointment.
     *
     * @throws IllegalArgumentException if the serviceTypeCode does not exist
     */
    @Transactional
    public Appointment book(AppointmentBookingRequest request) {
        if (!serviceTypeRepository.existsById(request.serviceTypeCode())) {
            throw new IllegalArgumentException(
                "Unknown service type code: " + request.serviceTypeCode()
            );
        }

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setCitizenName(request.citizenName());
        appointment.setCitizenEmail(request.citizenEmail());
        appointment.setServiceTypeCode(request.serviceTypeCode());
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setAppointmentTime(request.appointmentTime());
        appointment.setNotes(request.notes());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setDeleted(false);
        appointment.setCreatedAt(OffsetDateTime.now());
        appointment.setUpdatedAt(OffsetDateTime.now());

        return appointmentRepository.save(appointment);
    }

    /**
     * Cancels an existing appointment.
     *
     * @throws IllegalArgumentException if the appointment does not exist or is deleted
     * @throws IllegalStateException    if the appointment is already cancelled
     */
    @Transactional
    public Appointment cancel(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));

        if (appointment.isDeleted()) {
            throw new IllegalArgumentException("Appointment has been deleted: " + id);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Appointment is already cancelled: " + id);
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedAt(OffsetDateTime.now());
        return appointmentRepository.save(appointment);
    }

    /**
     * Finds an appointment by ID, excluding soft-deleted records.
     */
    @Transactional(readOnly = true)
    public Optional<Appointment> findById(UUID id) {
        return appointmentRepository.findById(id)
            .filter(a -> !a.isDeleted());
    }

    /**
     * Lists all active (non-deleted) appointments for a given citizen email.
     */
    @Transactional(readOnly = true)
    public List<Appointment> findByEmail(String email) {
        return appointmentRepository.findByCitizenEmailAndDeletedFalse(email);
    }

    /**
     * Finds all active appointments whose citizen name contains the given string
     * (case-insensitive). Partial match: works when the citizen gives only surname.
     */
    @Transactional(readOnly = true)
    public List<Appointment> findByCitizenName(String name) {
        return appointmentRepository.findByCitizenNameContainingIgnoreCaseAndDeletedFalse(name);
    }

    /**
     * Returns true if the slot is free (no BOOKED appointment for that service + date + time).
     * Rule: one appointment per slot per service type.
     */
    @Transactional(readOnly = true)
    public boolean isSlotAvailable(String serviceTypeCode, LocalDate date, LocalTime time) {
        return !appointmentRepository
            .existsByServiceTypeCodeAndAppointmentDateAndAppointmentTimeAndStatusAndDeletedFalse(
                serviceTypeCode, date, time, AppointmentStatus.BOOKED
            );
    }

    /**
     * Cancels a BOOKED appointment identified by citizenEmail + appointmentDate + serviceTypeCode.
     * Voice-friendly cancel: the citizen does not know their internal UUID.
     *
     * If multiple BOOKED rows match (edge case), the first one is cancelled.
     *
     * @throws IllegalArgumentException if no matching BOOKED appointment is found
     */
    @Transactional
    public Appointment cancelBySlot(String citizenEmail, LocalDate date, String serviceTypeCode) {
        List<Appointment> matches = appointmentRepository
            .findByCitizenEmailAndAppointmentDateAndServiceTypeCodeAndDeletedFalse(
                citizenEmail, date, serviceTypeCode
            );

        Appointment toCancel = matches.stream()
            .filter(a -> a.getStatus() == AppointmentStatus.BOOKED)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Nessun appuntamento attivo trovato per email=" + citizenEmail +
                ", data=" + date + ", servizio=" + serviceTypeCode
            ));

        return cancel(toCancel.getId());
    }
}
