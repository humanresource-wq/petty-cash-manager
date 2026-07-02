package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.DemoLoginRequest;
import com.freestone.pettycash.dto.GoogleLoginRequest;
import com.freestone.pettycash.dto.TokenResponse;
import com.freestone.pettycash.dto.UserResponse;
import com.freestone.pettycash.mapper.UserMapper;
import com.freestone.pettycash.model.User;
import com.freestone.pettycash.model.UserStatus;
import com.freestone.pettycash.repository.UserRepository;
import com.freestone.pettycash.security.UserPrincipal;
import com.freestone.pettycash.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Value("${app.security.demo-login-enabled:true}")
    private boolean demoLoginEnabled;

    @Value("${app.security.google.client-id:}")
    private String googleClientId;

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> loginGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginGoogle(request));
    }

    @PostMapping("/demo")
    public ResponseEntity<TokenResponse> loginDemo(@Valid @RequestBody DemoLoginRequest request) {
        return ResponseEntity.ok(authService.loginDemo(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userMapper.toResponse(principal.getUser()));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> appConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("demoLoginEnabled", demoLoginEnabled);
        config.put("googleClientId", googleClientId);

        if (demoLoginEnabled) {
            List<Map<String, String>> demoUsers = userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .map(u -> Map.of("id", u.getId(), "name", u.getName(), "email", u.getEmail()))
                    .toList();
            config.put("demoUsers", demoUsers);
        }

        return ResponseEntity.ok(config);
    }
}
