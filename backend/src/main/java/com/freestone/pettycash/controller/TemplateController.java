package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.TemplateRequest;
import com.freestone.pettycash.exception.ResourceNotFoundException;
import com.freestone.pettycash.model.ExpenseTemplate;
import com.freestone.pettycash.repository.ExpenseTemplateRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final ExpenseTemplateRepository templateRepository;

    @GetMapping
    public ResponseEntity<List<ExpenseTemplate>> listTemplates() {
        return ResponseEntity.ok(templateRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseTemplate> createTemplate(@Valid @RequestBody TemplateRequest request) {
        if (templateRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("Template with name '%s' already exists".formatted(request.name()));
        }

        ExpenseTemplate template = new ExpenseTemplate(
                request.name(),
                request.category(),
                request.description(),
                request.amount(),
                request.receiptRequired()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(templateRepository.save(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseTemplate> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest request) {
        
        ExpenseTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseTemplate", "id", id));

        // Check uniqueness if name changed
        if (!template.getName().equalsIgnoreCase(request.name()) && templateRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("Template with name '%s' already exists".formatted(request.name()));
        }

        template.setName(request.name());
        template.setCategory(request.category());
        template.setDescription(request.description());
        template.setAmount(request.amount());
        template.setReceiptRequired(request.receiptRequired());

        return ResponseEntity.ok(templateRepository.save(template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        if (!templateRepository.existsById(id)) {
            throw new ResourceNotFoundException("ExpenseTemplate", "id", id);
        }
        templateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
