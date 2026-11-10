package com.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    // Stored lowercase, immutable after registration, used for login and URLs.
    // The service must normalize (trim + toLowerCase(Locale.ROOT)) before saving or querying.
    @Indexed(unique = true)
    private String username;

    // Display name, stored uppercase, not unique, freely changeable.
    // The service must normalize (trim + collapse spaces + toUpperCase(Locale.ROOT)).
    private String profileName;

    // BCrypt hash only. Never the raw password.
    @ToString.Exclude
    private String passwordHash;

    @Builder.Default
    private Set<String> roles = new HashSet<>(Set.of("ROLE_USER"));

    @Builder.Default
    private Instant createdAt = Instant.now();
}