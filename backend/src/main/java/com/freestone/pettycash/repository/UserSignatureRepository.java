package com.freestone.pettycash.repository;

import com.freestone.pettycash.model.UserSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSignatureRepository extends JpaRepository<UserSignature, Long> {

    Optional<UserSignature> findByIdentifierIgnoreCase(String identifier);

    boolean existsByIdentifierIgnoreCase(String identifier);
}
