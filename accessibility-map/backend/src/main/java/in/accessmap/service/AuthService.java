package in.accessmap.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import in.accessmap.domain.AppUser;
import in.accessmap.dto.AuthRequest;
import in.accessmap.dto.AuthResponse;
import in.accessmap.repository.AppUserRepository;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        AppUser user = userRepository.save(new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                Instant.now()));
        return new AuthResponse(jwtService.issue(user.getEmail()), user.getEmail(), jwtService.getExpirationSeconds());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new AuthResponse(jwtService.issue(user.getEmail()), user.getEmail(), jwtService.getExpirationSeconds());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    }
}
