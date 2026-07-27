package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.SignatureResponse;
import com.freestone.pettycash.model.UserSignature;
import com.freestone.pettycash.service.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Controller for uploading, serving, listing, and managing DB-stored signatures.
 */
@RestController
@RequestMapping("/api/v1/signatures")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SignatureResponse> uploadSignature(
            @RequestParam("identifier") String identifier,
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) throws IOException {

        SignatureResponse response = signatureService.saveSignature(identifier, name, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SignatureResponse>> listSignatures() {
        return ResponseEntity.ok(signatureService.getAllSignatures());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getSignatureImage(@PathVariable Long id) {
        UserSignature signature = signatureService.getSignatureById(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(signature.getContentType()));
        return new ResponseEntity<>(signature.getSignatureData(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSignature(@PathVariable Long id) {
        signatureService.deleteSignature(id);
        return ResponseEntity.noContent().build();
    }
}
