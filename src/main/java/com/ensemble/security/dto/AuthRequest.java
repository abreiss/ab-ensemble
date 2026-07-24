package com.ensemble.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth} (username/password login, issue #14). Bean-validated
 * on bind ({@code @Valid}); a blank/malformed username (length outside 3–30, an illegal
 * character, or a leading/trailing separator) or a blank password fails binding and is rejected
 * with the shared sanitized {@code 400} before any repository or crypto work.
 */
public record AuthRequest(
	@NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$") String username,
	@NotBlank String password) {
}
