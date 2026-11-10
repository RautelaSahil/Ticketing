package com.ticketing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(null, null, null, null);
    }

    @Test
    @DisplayName("Should normalize username to lowercase and profile name to uppercase with trimmed spaces")
    void shouldNormalizeFieldsCorrectly() {
        String username = authService.validateAndNormalizeUsername("  JohnDoe-99  ");
        assertThat(username).isEqualTo("johndoe-99");

        String profileName = authService.validateAndNormalizeProfileName("   John    Smith   ");
        assertThat(profileName).isEqualTo("JOHN SMITH");
    }

    @Test
    @DisplayName("Should reject invalid usernames (length, hyphens, reserved words)")
    void shouldRejectInvalidUsernames() {
        assertThatThrownBy(() -> authService.validateAndNormalizeUsername("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 3 and 15");

        assertThatThrownBy(() -> authService.validateAndNormalizeUsername("-admin-"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.validateAndNormalizeUsername("user--name"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> authService.validateAndNormalizeUsername("admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("Should reject passwords shorter than 8 characters")
    void shouldRejectShortPasswords() {
        assertThatThrownBy(() -> authService.validatePassword("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");
    }
}
