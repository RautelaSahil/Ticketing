package com.ticketing.service;

import com.ticketing.dto.AuthResponse;
import com.ticketing.dto.LoginRequest;
import com.ticketing.dto.RegisterRequest;
import com.ticketing.model.User;
import com.ticketing.repository.UserRepository;
import com.ticketing.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "administrator", "system", "root", "anonymous",
            "null", "undefined", "api", "auth", "bot", "moderator", "guest"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedUsername = validateAndNormalizeUsername(request.getUsername());
        String normalizedProfileName = validateAndNormalizeProfileName(request.getProfileName());
        validatePassword(request.getPassword());

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already taken: " + normalizedUsername);
        }

        User user = User.builder()
                .username(normalizedUsername)
                .profileName(normalizedProfileName)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of("ROLE_USER"))
                .createdAt(Instant.now())
                .build();

        try {
            user = userRepository.save(user);
            log.info("Registered new user: {}", normalizedUsername);
        } catch (DuplicateKeyException e) {
            log.warn("Concurrent registration collision for username: {}", normalizedUsername);
            throw new IllegalArgumentException("Username is already taken: " + normalizedUsername);
        }

        // Auto-login after registration
        return performLogin(normalizedUsername, request.getPassword(), httpRequest, httpResponse);
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new BadCredentialsException("Username and password must not be empty");
        }
        String normalizedUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
        return performLogin(normalizedUsername, request.getPassword(), httpRequest, httpResponse);
    }

    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public AuthResponse getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        return AuthResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .profileName(principal.getProfileName())
                .roles(principal.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(java.util.stream.Collectors.toSet()))
                .build();
    }

    private AuthResponse performLogin(String username, String password, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return AuthResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .profileName(principal.getProfileName())
                .roles(principal.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(java.util.stream.Collectors.toSet()))
                .build();
    }

    public String validateAndNormalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() < 3 || normalized.length() > 15) {
            throw new IllegalArgumentException("Username must be between 3 and 15 characters");
        }

        if (!USERNAME_PATTERN.matcher(normalized).matches() || normalized.contains("--")) {
            throw new IllegalArgumentException(
                    "Username must contain only lowercase letters, digits, and single hyphens, and cannot begin or end with a hyphen"
            );
        }

        if (RESERVED_USERNAMES.contains(normalized)) {
            throw new IllegalArgumentException("Username '" + normalized + "' is reserved and cannot be used");
        }

        return normalized;
    }

    public String validateAndNormalizeProfileName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name cannot be empty");
        }
        String normalized = profileName.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        if (normalized.length() < 1 || normalized.length() > 30) {
            throw new IllegalArgumentException("Profile name must be between 1 and 30 characters");
        }

        return normalized;
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (password.length() > 100) {
            throw new IllegalArgumentException("Password must not exceed 100 characters");
        }
    }
}

