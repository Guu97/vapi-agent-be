package com.impruneta.vapiagent.appointment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for citizen appointment management.
 *
 * Endpoints:
 *   POST /appointments            – book a new appointment
 *   POST /appointments/{id}/cancel – cancel an existing appointment
 *   GET  /appointments/{id}       – retrieve a single appointment
 *   GET  /appointments?email=...  – list appointments by citizen email
 */
@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<Appointment> book(@Valid @RequestBody AppointmentBookingRequest request) {
        return ResponseEntity.ok(appointmentService.book(request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.cancel(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getById(@PathVariable UUID id) {
        return appointmentService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Appointment>> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(appointmentService.findByEmail(email));
    }
}
