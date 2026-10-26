package in.accessmap.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import in.accessmap.domain.AppUser;
import in.accessmap.repository.AppUserRepository;

@Component
public class ModeratorAccountInitializer implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(ModeratorAccountInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredEmail;
    private final String configuredPassword;

    public ModeratorAccountInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.moderation.email:}") String configuredEmail,
            @Value("${app.moderation.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredEmail = configuredEmail.trim().toLowerCase(Locale.ROOT);
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configuredEmail.isBlank() && configuredPassword.isBlank()) {
            logger.warn("No moderator account configured; community reports will remain pending.");
            return;
        }
        if (configuredEmail.isBlank() || configuredPassword.isBlank()) {
            throw new IllegalStateException(
                    "Set both ACCESSMAP_MODERATOR_EMAIL and ACCESSMAP_MODERATOR_PASSWORD, or leave both unset.");
        }
        int passwordBytes = configuredPassword.getBytes(StandardCharsets.UTF_8).length;
        if (configuredPassword.length() < 16 || passwordBytes > 72) {
            throw new IllegalStateException(
                    "ACCESSMAP_MODERATOR_PASSWORD must be at least 16 characters and at most 72 UTF-8 bytes.");
        }
        if (!configuredEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("ACCESSMAP_MODERATOR_EMAIL must be a valid email address.");
        }

        userRepository.findByEmail(configuredEmail).ifPresentOrElse(user -> {
            if (!passwordEncoder.matches(configuredPassword, user.getPasswordHash())) {
                throw new IllegalStateException(
                        "The configured moderator email already has a different password; use the account's correct password or choose another email.");
            }
            user.promoteToModerator();
        }, () -> userRepository.save(new AppUser(
                configuredEmail,
                passwordEncoder.encode(configuredPassword),
                Instant.now(),
                true)));
    }
}
