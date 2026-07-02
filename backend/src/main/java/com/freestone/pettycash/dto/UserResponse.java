package com.freestone.pettycash.dto;

import com.freestone.pettycash.model.Role;
import com.freestone.pettycash.model.UserStatus;

public record UserResponse(
        String id,
        String email,
        String name,
        Role role,
        UserStatus status
) {}
