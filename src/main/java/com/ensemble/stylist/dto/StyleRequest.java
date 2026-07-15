package com.ensemble.stylist.dto;

/**
 * Inbound style request: the free-text vibe the user typed (e.g. "streetwear
 * today"). A DTO at the API boundary — the controller never exposes stylist
 * internals.
 *
 * @param prompt the free-text vibe
 */
public record StyleRequest(String prompt) {
}
