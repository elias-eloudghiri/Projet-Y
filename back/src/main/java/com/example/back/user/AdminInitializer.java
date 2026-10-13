package com.example.back.user;

import com.example.back.config.AuthProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator at startup from ADMIN_PSEUDO / ADMIN_PASSWORD when no admin exists yet. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        String password = properties.adminPassword();
        if (password == null || password.isBlank()) {
            log.warn("No administrator exists and ADMIN_PASSWORD is not set: no admin account created");
            return;
        }
        String pseudo = PseudoGenerator.normalize(properties.adminPseudo());
        if (!PseudoGenerator.FORMAT.matcher(pseudo).matches()) {
            throw new IllegalStateException("ADMIN_PSEUDO must match " + PseudoGenerator.FORMAT.pattern());
        }
        if (userRepository.existsByPseudo(pseudo)) {
            throw new IllegalStateException("ADMIN_PSEUDO " + pseudo + " is already used by a non-admin account");
        }
        userRepository.save(new User(pseudo, passwordEncoder.encode(password), Role.ADMIN, clock.instant()));
        log.info("Administrator account created with pseudo {}", pseudo);
    }
}
