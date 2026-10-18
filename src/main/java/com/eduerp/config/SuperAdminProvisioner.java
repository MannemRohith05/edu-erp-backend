package com.eduerp.config;

import com.eduerp.entity.Role;
import com.eduerp.entity.User;
import com.eduerp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Performs an opt-in, one-time bootstrap of the existing administrator role.
 * The existing ADMINISTRATOR enum value is the application's Super Admin role.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminProvisioner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.first-name:}")
    private String firstName;

    @Value("${app.super-admin.last-name:Super Admin}")
    private String lastName;

    @Value("${app.super-admin.email:}")
    private String email;

    @Value("${app.super-admin.initial-password:}")
    private String initialPassword;

    @Override
    @Transactional
    public synchronized void run(String... args) {
        if (userRepository.countByRole(Role.ADMINISTRATOR) > 0) {
            log.info("Super Admin provisioning skipped because an administrator account already exists.");
            return;
        }

        if (!StringUtils.hasText(firstName)
                || !StringUtils.hasText(lastName)
                || !StringUtils.hasText(email)
                || !StringUtils.hasText(initialPassword)) {
            log.info("Super Admin provisioning is disabled because required environment values are not configured.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            log.info("Super Admin provisioning skipped because the configured account already exists.");
            return;
        }

        User superAdmin = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(initialPassword))
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .role(Role.ADMINISTRATOR)
                .active(true)
                .build();

        userRepository.save(superAdmin);
        log.info("Super Admin account provisioned successfully.");
    }
}
