package com.freestone.pettycash.service;

import com.freestone.pettycash.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service to resolve and load user digital signature image bytes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureService {

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;

    /**
     * Resolves the signature image bytes for a given user identifier (email, username, or role key).
     *
     * @param userIdentifier User email, username, or role identifier
     * @return byte[] containing image data (PNG/JPEG) or null if signature is not found
     */
    public byte[] getSignatureForUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.trim().isBlank()) {
            return null;
        }

        String identifier = userIdentifier.trim();

        // 1. Check explicit map in configuration
        Map<String, String> userMap = appProperties.getSignatures().getUserMap();
        String mappedFilename = null;
        if (userMap != null) {
            mappedFilename = userMap.get(identifier);
            if (mappedFilename == null) {
                // Try case-insensitive lookup
                for (Map.Entry<String, String> entry : userMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(identifier)) {
                        mappedFilename = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (mappedFilename != null) {
            byte[] imageBytes = loadResourceBytes(mappedFilename);
            if (imageBytes != null) {
                return imageBytes;
            }
        }

        // 2. Try convention-based lookups using directory property
        String dir = appProperties.getSignatures().getDirectory();
        if (dir == null || dir.isBlank()) {
            dir = "classpath:signatures/";
        }
        if (!dir.endsWith("/")) {
            dir += "/";
        }

        // Generate candidate filenames for convention lookup
        String safeIdentifier = identifier.replaceAll("[^a-zA-Z0-9._-]", "_");
        List<String> candidates = new java.util.ArrayList<>(List.of(
                dir + safeIdentifier + ".png",
                dir + safeIdentifier + ".jpg",
                dir + safeIdentifier + ".jpeg",
                dir + safeIdentifier + "_sign.png",
                dir + safeIdentifier + "_sign.jpg",
                dir + identifier + ".png",
                dir + identifier + ".jpg"
        ));

        if (identifier.contains("@")) {
            String usernamePart = identifier.substring(0, identifier.indexOf('@'));
            candidates.add(dir + usernamePart + ".png");
            candidates.add(dir + usernamePart + ".jpg");
            candidates.add(dir + usernamePart + "_sign.png");
            candidates.add(dir + usernamePart + "_sign.jpg");
        }

        // Smart role and domain fallbacks
        if (identifier.toLowerCase().contains("admin") || identifier.toLowerCase().contains("approved")) {
            candidates.add(dir + "admin_sign.png");
        } else if (identifier.contains("@") || identifier.toLowerCase().contains("surve") || identifier.toLowerCase().contains("harshada")) {
            candidates.add(dir + "payer_sign.png");
        } else {
            candidates.add(dir + "payee_sign.png");
        }

        for (String candidate : candidates) {
            byte[] imageBytes = loadResourceBytes(candidate);
            if (imageBytes != null) {
                return imageBytes;
            }
        }

        log.debug("No signature image found for user identifier: {}", userIdentifier);
        return null;
    }

    private byte[] loadResourceBytes(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }

        try {
            String resourcePath = location;
            if (!location.startsWith("classpath:") && !location.startsWith("file:") && !location.startsWith("/")) {
                String dir = appProperties.getSignatures().getDirectory();
                if (dir == null || dir.isBlank()) {
                    dir = "classpath:signatures/";
                }
                if (!dir.endsWith("/")) {
                    dir += "/";
                }
                resourcePath = dir + location;
            }

            Resource resource = resourceLoader.getResource(resourcePath);
            if (resource.exists() && resource.isReadable()) {
                try (InputStream is = resource.getInputStream()) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load signature resource from location '{}': {}", location, e.getMessage());
        }
        return null;
    }
}
