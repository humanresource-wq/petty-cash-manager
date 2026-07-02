package com.freestone.pettycash.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
        @NotBlank(message = "User ID must not be blank")
        String userId
) {}
