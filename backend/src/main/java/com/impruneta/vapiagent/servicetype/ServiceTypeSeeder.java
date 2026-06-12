package com.impruneta.vapiagent.servicetype;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ensures the mandatory service type codes are present in the database at startup.
 *
 * This runs once per application start. It uses existsById to avoid duplicate inserts,
 * making it safe to run on every startup (idempotent).
 *
 * In a CI/CD pipeline, this would be replaced by a Flyway or Liquibase seed migration.
 * For this assignment, a simple @PostConstruct component keeps things explicit and portable.
 */
@Component
public class ServiceTypeSeeder {

    private static final Logger log = LoggerFactory.getLogger(ServiceTypeSeeder.class);

    private final ServiceTypeRepository serviceTypeRepository;

    public ServiceTypeSeeder(ServiceTypeRepository serviceTypeRepository) {
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @PostConstruct
    public void seed() {
        seedIfAbsent("ANAGRAFE",        "Anagrafe e Stato Civile",    "Servizi anagrafici, stato civile e residenza");
        seedIfAbsent("TRIBUTI",         "Tributi",                     "Gestione tributi comunali, IMU e TARI");
        seedIfAbsent("URP",             "Ufficio Relazioni Pubblico",  "Informazioni generali, orari uffici e segnalazioni");
        seedIfAbsent("SERVIZI_SOCIALI", "Servizi Sociali e Sanità",    "Servizi sociali, assistenza e sanità");
        log.info("Service type seeding complete");
    }

    private void seedIfAbsent(String code, String name, String description) {
        if (!serviceTypeRepository.existsById(code)) {
            serviceTypeRepository.save(new ServiceType(code, name, description));
            log.info("Seeded service type: {}", code);
        }
    }
}
