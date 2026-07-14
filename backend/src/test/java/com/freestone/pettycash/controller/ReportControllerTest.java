package com.freestone.pettycash.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/reports/export/pdf with authenticated user should return 200 OK and PDF attachment")
    @WithUserDetails("google-sub-harsh")
    void exportPdfReturns200AndPdf() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("company", "Freestone Technologies LLP")
                        .param("type", "EXPENSE")
                        .param("search", "coffee"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("GET /api/v1/reports/export/csv with authenticated user should return 200 OK and CSV attachment")
    @WithUserDetails("google-sub-harsh")
    void exportCsvReturns200AndCsv() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/csv")
                        .param("categoryName", "Stationery")
                        .param("type", "EXPENSE")
                        .param("search", "markers"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(content().contentType(org.springframework.http.MediaType.parseMediaType("text/csv")));
    }
}
