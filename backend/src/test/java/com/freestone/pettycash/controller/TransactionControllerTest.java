package com.freestone.pettycash.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freestone.pettycash.dto.TransactionRequest;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.dto.TransactionUpdateRequest;
import com.freestone.pettycash.model.*;
import com.freestone.pettycash.repository.CashBoxRepository;
import com.freestone.pettycash.repository.CategoryRepository;
import com.freestone.pettycash.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CashBoxRepository cashBoxRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.findByNameIgnoreCase("Stationery")
                .orElseGet(() -> categoryRepository.save(new Category("Stationery")));
    }

    @Test
    @DisplayName("POST /api/v1/transactions with amount exceeding cashbox balance should return 400 Bad Request")
    @WithUserDetails("google-sub-harsh")
    void createExpenseExceedingBalanceReturns400() throws Exception {
        // Setup cashbox to 100.00
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(100.00));
        cashBoxRepository.save(box);

        TransactionRequest request = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(200.00), // 200 > 100
                "Expense exceeding balance",
                LocalDate.now(),
                "Vendor A",
                testCategory.getId(),
                null,
                "VCH-CONT-001",
                "Freestone Infotech LLP"
        );

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(multipart("/api/v1/transactions")
                        .file(requestPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient balance in Cash Box. Requested: Rs. 200.00, Available: Rs. 100.00"));
    }

    @Test
    @DisplayName("PUT /api/v1/transactions/{id} with amount exceeding cashbox balance should return 400 Bad Request")
    @WithUserDetails("google-sub-harsh")
    void updateExpenseExceedingBalanceReturns400() throws Exception {
        // Setup cashbox to 500.00
        CashBox box = cashBoxRepository.findById(1L).orElseThrow();
        box.setBalance(BigDecimal.valueOf(500.00));
        cashBoxRepository.save(box);

        // Record initial expense of 100.00 (cashbox balance becomes 400.00)
        TransactionRequest createReq = new TransactionRequest(
                TransactionType.EXPENSE,
                BigDecimal.valueOf(100.00),
                "Initial expense",
                LocalDate.now(),
                "Vendor B",
                testCategory.getId(),
                null,
                "VCH-CONT-002",
                "Freestone Infotech LLP"
        );
        TransactionResponse created = transactionService.recordTransaction(createReq, "google-sub-harsh", null, null, null);

        // Attempt update to 1000.00 (restores 100.00 to cashbox balance -> 500.00, but 1000.00 > 500.00)
        TransactionUpdateRequest updateRequest = new TransactionUpdateRequest(
                BigDecimal.valueOf(1000.00),
                "Updated expense exceeding",
                LocalDate.now(),
                "Vendor B",
                testCategory.getId(),
                null,
                "VCH-CONT-002",
                "Freestone Infotech LLP"
        );

        mockMvc.perform(put("/api/v1/transactions/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient balance in Cash Box. Requested: Rs. 1000.00, Available: Rs. 500.00"));
    }
}
