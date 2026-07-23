package com.ensemble.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth} (email/password login, issue #14). Bean-validated
 * on bind ({@code @Valid}); a blank/invalid email or blank password fails binding and is
 * rejected with the shared sanitized {@code 400} before any repository or crypto work.
 */
public record AuthRequest(@NotBlank @Email String email, @NotBlank String password) {
}
