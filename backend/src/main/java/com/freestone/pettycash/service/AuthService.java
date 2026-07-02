package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.DemoLoginRequest;
import com.freestone.pettycash.dto.GoogleLoginRequest;
import com.freestone.pettycash.dto.TokenResponse;
import com.freestone.pettycash.dto.UserResponse;
import com.freestone.pettycash.mapper.UserMapper;
import com.freestone.pettycash.model.User;
import com.freestone.pettycash.model.UserStatus;
import com.freestone.pettycash.repository.UserRepository;
import com.freestone.pettycash.security.GoogleTokenVerifier;
import com.freestone.pettycash.security.JwtTokenProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating OAuth Google verifications and Demo login workflows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Value("${app.security.demo-login-enabled:true}")
    private boolean demoLoginEnabled;

    @Value("${app.security.allowed-domain:}")
    private String allowedDomain;

    @Transactional(readOnly = true)
    public TokenResponse loginGoogle(GoogleLoginRequest request) {
        try {
            GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.credential());
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String googleSub = payload.getSubject();

            // Restrict by allowed domain if configured
            if (allowedDomain != null && !allowedDomain.isBlank()) {
                String domainSuffix = "@" + allowedDomain.toLowerCase();
                if (!email.toLowerCase().endsWith(domainSuffix)) {
                    throw new BadCredentialsException("Sign-in restricted to domain: " + allowedDomain);
                }
            }

            User user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "Email %s is not authorized in the petty cash directory".formatted(email)));

            if (user.getStatus() == UserStatus.INACTIVE) {
                throw new BadCredentialsException("User account is inactive");
            }

            // Optional: Update user's Google sub ID if not present (helps map demo users to oauth subs)
            if (user.getId().startsWith("google-sub-") || !user.getId().equals(googleSub)) {
                // We'll update the user ID mapping in database if we want, or keep it.
                // For simplicity, we just use the existing DB user id
            }

            String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
            return new TokenResponse(token, userMapper.toResponse(user));

        } catch (Exception e) {
            log.warn("Google authentication failed", e);
            throw new BadCredentialsException("Google authentication failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse loginDemo(DemoLoginRequest request) {
        if (!demoLoginEnabled) {
            throw new IllegalStateException("Demo logins are disabled on this environment");
        }

        User user = userRepository.findById(request.userId())
                .or(() -> userRepository.findByEmailIgnoreCase(request.userId()))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Demo user not found with credentials: " + request.userId()));

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BadCredentialsException("User account is inactive");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenResponse(token, userMapper.toResponse(user));
    }
}
