package com.freestone.pettycash.service;

import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Service integrating Google Drive API using service account credentials.
 * Mirroring drive.py logic from hr-portal.
 */
@Service
@Slf4j
public class GoogleDriveService {

    @Value("${app.google.drive.private-key:}")
    private String privateKey;

    @Value("${app.google.drive.client-email:}")
    private String clientEmail;

    @Value("${app.google.drive.client-id:}")
    private String clientId;

    @Value("${app.google.drive.private-key-id:}")
    private String privateKeyId;

    @Value("${app.google.drive.parent-folder-id:}")
    private String parentFolderId;

    private Drive driveService;
    private String pettyCashReceiptsFolderId;

    @PostConstruct
    public void init() {
        if (privateKey == null || privateKey.isBlank() || clientEmail == null || clientEmail.isBlank()) {
            log.warn("Google Drive service account credentials are not configured. Uploads will be mocked locally.");
            return;
        }

        try {
            String cleanKey = normalizePem(privateKey);
            String credentialsJson = String.format(
                    "{\n" +
                    "  \"type\": \"service_account\",\n" +
                    "  \"private_key_id\": \"%s\",\n" +
                    "  \"private_key\": \"%s\",\n" +
                    "  \"client_email\": \"%s\",\n" +
                    "  \"client_id\": \"%s\",\n" +
                    "  \"token_uri\": \"https://oauth2.googleapis.com/token\"\n" +
                    "}",
                    (privateKeyId != null && !privateKeyId.isBlank()) ? privateKeyId : "dummy_private_key_id",
                    cleanKey.replace("\n", "\\n"), // Escape newlines for JSON payload
                    clientEmail,
                    (clientId != null && !clientId.isBlank()) ? clientId : "100000000000000000000"
            );

            InputStream credentialsStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

            this.driveService = new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Petty Cash Manager")
                    .build();

            log.info("Google Drive service initialized successfully. Checking baseline folders...");
            if (parentFolderId != null && !parentFolderId.isBlank()) {
                // Idempotently create our main folder "petty-cash-receipts" inside parent folder
                this.pettyCashReceiptsFolderId = getOrCreateFolder("petty-cash-receipts", parentFolderId);
                log.info("Verified petty-cash-receipts folder ID: {}", pettyCashReceiptsFolderId);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service", e);
        }
    }

    private String normalizePem(String key) {
        key = key.trim();
        // Replace literal \n (2-char backslash+n from unquoted .env values)
        if (key.contains("\\n")) {
            key = key.replace("\\n", "\n");
        }
        // Normalize Windows line endings
        key = key.replace("\r\n", "\n").replace("\r", "");
        if (!key.endsWith("\n")) {
            key += "\n";
        }
        return key;
    }

    /**
     * Finds or creates a subfolder idempotently.
     */
    public String getOrCreateFolder(String name, String parentId) throws IOException {
        if (driveService == null) {
            return "mock-folder-id-" + name;
        }

        String safeName = name.replace("\\", "\\\\").replace("'", "\\'");
        String query = String.format(
                "name = '%s' and '%s' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
                safeName, parentId
        );

        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id)")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }

        File folderMetadata = new File();
        folderMetadata.setName(name);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(parentId));

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .setSupportsAllDrives(true)
                .execute();

        return folder.getId();
    }

    /**
     * Uploads a file inside a specific transaction folder.
     */
    public String uploadFile(String transactionNo, String filename, byte[] fileBytes, String mimeType) throws IOException {
        if (driveService == null) {
            log.info("Google Drive not configured. Mocking file upload: {}", filename);
            return "mock-file-id-" + System.currentTimeMillis();
        }

        // Get or create folder for this specific transaction inside our root receipts folder
        String folderId = getOrCreateFolder(transactionNo, pettyCashReceiptsFolderId != null ? pettyCashReceiptsFolderId : parentFolderId);

        File fileMetadata = new File();
        fileMetadata.setName(filename);
        fileMetadata.setParents(Collections.singletonList(folderId));

        InputStreamContent mediaContent = new InputStreamContent(mimeType, new ByteArrayInputStream(fileBytes));

        File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .setSupportsAllDrives(true)
                .execute();

        return uploadedFile.getId();
    }

    /**
     * Downloads file bytes from Google Drive.
     */
    public byte[] downloadFile(String fileId) throws IOException {
        if (driveService == null) {
            // Return mock text for local debug if Google Drive is not configured
            return "Mock file contents: Google Drive is not configured.".getBytes(StandardCharsets.UTF_8);
        }
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Deletes a file from Google Drive.
     */
    public void deleteFile(String fileId) throws IOException {
        if (driveService == null) {
            log.info("Google Drive not configured. Mocking file deletion: {}", fileId);
            return;
        }
        driveService.files().delete(fileId)
                .setSupportsAllDrives(true)
                .execute();
    }
}
