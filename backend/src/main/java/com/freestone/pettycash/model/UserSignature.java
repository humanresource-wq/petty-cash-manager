package com.freestone.pettycash.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity representing a user digital signature stored as a BLOB in the database.
 */
@Entity
@Table(name = "user_signatures")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String identifier;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(name = "signature_data", nullable = false)
    private byte[] signatureData;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public UserSignature(String identifier, String name, byte[] signatureData, String contentType) {
        this.identifier = identifier;
        this.name = name;
        this.signatureData = signatureData;
        this.contentType = contentType;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
