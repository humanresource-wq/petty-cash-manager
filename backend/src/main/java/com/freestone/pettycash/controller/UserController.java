package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.UserResponse;
import com.freestone.pettycash.exception.ResourceNotFoundException;
import com.freestone.pettycash.mapper.UserMapper;
import com.freestone.pettycash.model.Role;
import com.freestone.pettycash.model.User;
import com.freestone.pettycash.model.UserStatus;
import com.freestone.pettycash.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("User with email '%s' already exists".formatted(request.email()));
        }

        // Standard user ID defaults to username part of email if not provided
        String id = request.email().split("@")[0].toLowerCase();
        User user = new User(id, request.email(), request.name(), request.role());
        return ResponseEntity.ok(userMapper.toResponse(userRepository.save(user)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserStatus(@PathVariable String id, @RequestParam UserStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setStatus(status);
        return ResponseEntity.ok(userMapper.toResponse(userRepository.save(user)));
    }

    // Helper request record DTO
    public record CreateUserRequest(
            @NotBlank(message = "Name must not be blank")
            String name,
            @NotBlank(message = "Email must not be blank")
            @Email(message = "Invalid email format")
            String email,
            @NotNull(message = "Role must not be null")
            Role role
    ) {}
}
