package com.example.back.auth;

import com.example.back.user.PseudoGenerator;
import com.example.back.user.Role;
import com.example.back.user.User;
import com.example.back.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_PSEUDO_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PseudoGenerator pseudoGenerator;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;

    /**
     * Hash checked when the pseudo does not exist, so that response time does not reveal existing
     * accounts.
     */
    private volatile String dummyHash;

    public record Session(User user, String accessToken, String refreshToken) {}

    public static class InvalidCredentialsException extends RuntimeException {}

    public static class TooManyAttemptsException extends RuntimeException {
        private final Duration retryAfter;

        public TooManyAttemptsException(Duration retryAfter) {
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    public Session register(String password) {
        String hash = passwordEncoder.encode(password);
        for (int attempt = 0; attempt < MAX_PSEUDO_ATTEMPTS; attempt++) {
            String pseudo = pseudoGenerator.generate();
            if (userRepository.existsByPseudo(pseudo)) {
                continue;
            }
            try {
                User user =
                        userRepository.saveAndFlush(
                                new User(pseudo, hash, Role.USER, clock.instant()));
                return openSession(user);
            } catch (DataIntegrityViolationException e) {
                // Pseudo taken concurrently: try another one.
            }
        }
        throw new IllegalStateException("Could not generate a unique pseudo");
    }

    public Session login(String pseudoInput, String password, String ip) {
        String pseudo = PseudoGenerator.normalize(pseudoInput);
        loginAttemptService
                .blockedFor(pseudo, ip)
                .ifPresent(
                        remaining -> {
                            throw new TooManyAttemptsException(remaining);
                        });

        Optional<User> user = userRepository.findByPseudo(pseudo);
        boolean matches;
        if (user.isPresent()) {
            matches = passwordEncoder.matches(password, user.get().getPasswordHash());
        } else {
            passwordEncoder.matches(password, dummyHash());
            matches = false;
        }
        if (!matches) {
            loginAttemptService.recordFailure(pseudo, ip);
            throw new InvalidCredentialsException();
        }

        loginAttemptService.reset(pseudo, ip);
        User loggedIn = user.get();
        loggedIn.setLastLoginAt(clock.instant());
        userRepository.save(loggedIn);
        return openSession(loggedIn);
    }

    public Optional<Session> refresh(String refreshToken) {
        return refreshTokenService
                .rotate(refreshToken)
                .map(
                        r ->
                                new Session(
                                        r.user(),
                                        accessTokenService.issue(r.user()),
                                        r.refreshToken()));
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private Session openSession(User user) {
        return new Session(user, accessTokenService.issue(user), refreshTokenService.issue(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equalizer-password");
        }
        return dummyHash;
    }
}
