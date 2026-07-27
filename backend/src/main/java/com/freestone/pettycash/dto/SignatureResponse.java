package com.freestone.pettycash.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SignatureResponse {
    private Long id;
    private String identifier;
    private String name;
    private String contentType;
    private LocalDateTime createdAt;
}
