package com.freestone.pettycash.dto;

import java.time.LocalDateTime;

/**
 * Standard error response DTO for consistent API error formatting.
 */
public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp
) {
    public ErrorResponse(String code, String message) {
        this(code, message, LocalDateTime.now());
    }
}
