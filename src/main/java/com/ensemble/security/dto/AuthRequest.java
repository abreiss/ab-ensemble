package com.ensemble.security.dto;

/** Request body for {@code POST /api/auth}. */
public record AuthRequest(String passcode) {
}
