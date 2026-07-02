package com.freestone.pettycash.dto;

public record TokenResponse(
        String token,
        UserResponse user
) {}
