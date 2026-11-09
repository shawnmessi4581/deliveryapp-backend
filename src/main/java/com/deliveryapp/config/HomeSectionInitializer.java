package com.deliveryapp.config;

import com.deliveryapp.service.HomeSectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Creates any missing home section (one per type) at startup, so the admin only edits them. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeSectionInitializer implements CommandLineRunner {

    private final HomeSectionService homeSectionService;

    @Override
    public void run(String... args) {
        try {
            homeSectionService.createMissingSections();
        } catch (RuntimeException e) {
            // Only the new home screen depends on these rows — never stop the backend over them
            log.error("Could not create the default home sections", e);
        }
    }
}
