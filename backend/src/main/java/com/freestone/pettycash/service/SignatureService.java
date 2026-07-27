package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.SignatureResponse;
import com.freestone.pettycash.model.UserSignature;
import com.freestone.pettycash.repository.UserSignatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service to resolve, standardize, and manage user digital signatures stored in the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private static final int STANDARD_MAX_WIDTH = 400;
    private static final int STANDARD_MAX_HEIGHT = 150;

    private final UserSignatureRepository userSignatureRepository;

    /**
     * Resolves signature bytes from database using exact, word-boundary, and partial matching.
     *
     * @param userIdentifier User email, payee name, or identifier
     * @return byte[] containing image bytes (PNG) or null if signature is not found
     */
    @Transactional(readOnly = true)
    public byte[] getSignatureForUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.trim().isBlank()) {
            return null;
        }

        String search = userIdentifier.trim();
        List<UserSignature> allSignatures = userSignatureRepository.findAll();
        if (allSignatures.isEmpty()) {
            log.debug("No signatures stored in DB for lookup: {}", userIdentifier);
            return null;
        }

        // 1. Exact match (case-insensitive) on identifier
        for (UserSignature sig : allSignatures) {
            if (sig.getIdentifier().equalsIgnoreCase(search)) {
                return sig.getSignatureData();
            }
        }

        // 2. Word boundary matching (e.g., key "santosh" matching "santosh shelke (swiggy )")
        for (UserSignature sig : allSignatures) {
            if (matchesWordBoundary(sig.getIdentifier(), search)) {
                return sig.getSignatureData();
            }
        }

        // 3. Partial substring contains match
        for (UserSignature sig : allSignatures) {
            if (matchesSubstring(sig.getIdentifier(), search)) {
                return sig.getSignatureData();
            }
        }

        log.debug("No DB signature match found for user identifier: {}", userIdentifier);
        return null;
    }

    /**
     * Uploads and saves a user signature to the database after standardizing its dimensions.
     */
    @Transactional
    public SignatureResponse saveSignature(String identifier, MultipartFile file) throws IOException {
        if (identifier == null || identifier.trim().isBlank()) {
            throw new IllegalArgumentException("Identifier must not be blank");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Signature image file must not be empty");
        }

        byte[] resizedBytes = resizeImageToStandard(file.getBytes(), STANDARD_MAX_WIDTH, STANDARD_MAX_HEIGHT);
        String cleanIdentifier = identifier.trim();

        Optional<UserSignature> existingOpt = userSignatureRepository.findByIdentifierIgnoreCase(cleanIdentifier);
        UserSignature userSignature;
        if (existingOpt.isPresent()) {
            userSignature = existingOpt.get();
            userSignature.setSignatureData(resizedBytes);
            userSignature.setContentType("image/png");
        } else {
            userSignature = new UserSignature(cleanIdentifier, resizedBytes, "image/png");
        }

        UserSignature saved = userSignatureRepository.save(userSignature);
        return toResponse(saved);
    }

    /**
     * Lists all uploaded signature metadata.
     */
    @Transactional(readOnly = true)
    public List<SignatureResponse> getAllSignatures() {
        return userSignatureRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets a single signature entity by ID.
     */
    @Transactional(readOnly = true)
    public UserSignature getSignatureById(Long id) {
        return userSignatureRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Signature not found with ID: " + id));
    }

    /**
     * Deletes a signature from the database by ID.
     */
    @Transactional
    public void deleteSignature(Long id) {
        if (!userSignatureRepository.existsById(id)) {
            throw new IllegalArgumentException("Signature not found with ID: " + id);
        }
        userSignatureRepository.deleteById(id);
    }

    /**
     * Resizes an input image to standard maximum bounds while maintaining aspect ratio.
     */
    public byte[] resizeImageToStandard(byte[] originalBytes, int maxWidth, int maxHeight) throws IOException {
        System.setProperty("java.awt.headless", "true");

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image file format");
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(1.0, Math.min(widthRatio, heightRatio));

        int targetWidth = (int) Math.round(originalWidth * ratio);
        int targetHeight = (int) Math.round(originalHeight * ratio);

        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", baos);
        return baos.toByteArray();
    }

    private boolean matchesWordBoundary(String dbKey, String search) {
        if (dbKey == null || search == null) {
            return false;
        }
        String k = dbKey.trim().toLowerCase();
        String s = search.trim().toLowerCase();
        if (k.isEmpty() || s.isEmpty()) {
            return false;
        }

        if (k.matches("[a-zA-Z0-9_]+")) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(k) + "\\b");
            if (pattern.matcher(s).find()) {
                return true;
            }
        }
        if (s.matches("[a-zA-Z0-9_]+")) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(s) + "\\b");
            if (pattern.matcher(k).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSubstring(String dbKey, String search) {
        if (dbKey == null || search == null) {
            return false;
        }
        String k = dbKey.trim().toLowerCase();
        String s = search.trim().toLowerCase();
        if (k.length() < 2 || s.length() < 2) {
            return false;
        }
        return s.contains(k) || k.contains(s);
    }

    private SignatureResponse toResponse(UserSignature sig) {
        return new SignatureResponse(
                sig.getId(),
                sig.getIdentifier(),
                sig.getContentType(),
                sig.getCreatedAt()
        );
    }
}
