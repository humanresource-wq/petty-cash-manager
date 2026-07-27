package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.SignatureResponse;
import com.freestone.pettycash.repository.UserSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SignatureServiceTest {

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private UserSignatureRepository userSignatureRepository;

    @BeforeEach
    void setUp() {
        userSignatureRepository.deleteAll();
    }

    @Test
    @DisplayName("getSignatureForUser returns null when user identifier is null or empty")
    void returnsNullForBlankUser() {
        assertThat(signatureService.getSignatureForUser(null)).isNull();
        assertThat(signatureService.getSignatureForUser("  ")).isNull();
    }

    @Test
    @DisplayName("getSignatureForUser returns null when no matching signature exists in DB")
    void returnsNullWhenSignatureNotFound() {
        assertThat(signatureService.getSignatureForUser("nonexistent_user_12345@example.com")).isNull();
    }

    @Test
    @DisplayName("saveSignature and getSignatureForUser with exact and word-boundary fuzzy matching")
    void testSaveAndFuzzyMatching() throws IOException {
        byte[] dummyImageBytes = createSampleImage(600, 300);
        MockMultipartFile file = new MockMultipartFile("file", "santosh.png", "image/png", dummyImageBytes);

        SignatureResponse response = signatureService.saveSignature("santosh", "Santosh Shelke", file);
        assertThat(response).isNotNull();
        assertThat(response.getIdentifier()).isEqualTo("santosh");

        // 1. Exact match
        byte[] exactSig = signatureService.getSignatureForUser("santosh");
        assertThat(exactSig).isNotNull();

        // 2. Word boundary fuzzy match ("santosh shelke (swiggy )")
        byte[] fuzzySig = signatureService.getSignatureForUser("santosh shelke (swiggy )");
        assertThat(fuzzySig).isNotNull();
        assertThat(fuzzySig).isEqualTo(exactSig);
    }

    private byte[] createSampleImage(int width, int height) throws IOException {
        System.setProperty("java.awt.headless", "true");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
