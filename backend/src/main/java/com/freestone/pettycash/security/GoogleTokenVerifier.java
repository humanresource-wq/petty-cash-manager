package com.freestone.pettycash.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Validates Google OAuth2 ID tokens signature, audience, and expiration using Google Client libraries.
 */
@Component
@Slf4j
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.security.google.client-id:}") String googleClientId) {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.warn("GOOGLE_CLIENT_ID is not configured. Google OAuth login will not be operational.");
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
        }
    }

    public GoogleIdToken.Payload verify(String idTokenString) throws GeneralSecurityException, IOException {
        if (verifier == null) {
            throw new IllegalStateException("Google OAuth is not configured on this server.");
        }
        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken != null) {
            return idToken.getPayload();
        } else {
            throw new IllegalArgumentException("Invalid Google ID token signature or expired");
        }
    }
}
