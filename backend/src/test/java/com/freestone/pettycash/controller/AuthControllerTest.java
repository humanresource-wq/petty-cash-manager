package com.freestone.pettycash.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freestone.pettycash.dto.DemoLoginRequest;
import com.freestone.pettycash.model.User;
import com.freestone.pettycash.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/auth/config should return system configuration settings")
    void getAppConfigReturnsConfig() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoLoginEnabled").value(true))
                .andExpect(jsonPath("$.demoUsers").isArray())
                .andExpect(jsonPath("$.demoUsers[0].email").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/demo should authenticate valid seeded users")
    void loginDemoWithSeededUserSucceeds() throws Exception {
        // Priya is a seeded admin user in changes/002-core-entities.xml
        User seededUser = userRepository.findByEmailIgnoreCase("dolly.chheda@freestoneinfotech.com").orElseThrow();

        DemoLoginRequest request = new DemoLoginRequest(seededUser.getId());

        mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("dolly.chheda@freestoneinfotech.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/demo with invalid user should return 401/404 or bad credentials")
    void loginDemoWithInvalidUserFails() throws Exception {
        DemoLoginRequest request = new DemoLoginRequest("non-existent-user");

        mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // Handled by BadCredentialsException / GlobalExceptionHandler
    }

    @Test
    @DisplayName("Accessing protected endpoints without token should return 403 Forbidden or 401 Unauthorized")
    void accessingProtectedEndpointsWithoutTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isForbidden()); // Spring Security default without valid authentication filter context
    }
}
