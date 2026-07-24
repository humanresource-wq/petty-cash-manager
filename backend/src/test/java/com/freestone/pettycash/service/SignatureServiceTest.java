package com.freestone.pettycash.service;

import com.freestone.pettycash.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SignatureServiceTest {

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private AppProperties appProperties;

    @Test
    @DisplayName("getSignatureForUser returns null when user identifier is null or empty")
    void returnsNullForBlankUser() {
        assertThat(signatureService.getSignatureForUser(null)).isNull();
        assertThat(signatureService.getSignatureForUser("  ")).isNull();
    }

    @Test
    @DisplayName("getSignatureForUser returns null when no matching image exists")
    void returnsNullWhenSignatureNotFound() {
        assertThat(signatureService.getSignatureForUser("nonexistent_user_12345@example.com")).isNull();
    }
}
