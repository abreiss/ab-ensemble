package com.ensemble.security.dto;

/** Response body for a successful {@code POST /api/auth}. */
public record AuthResponse(String token) {
}
