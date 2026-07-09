package com.freestone.pettycash.controller;

import com.freestone.pettycash.dto.CategoryResponse;
import com.freestone.pettycash.dto.SubcategoryResponse;
import com.freestone.pettycash.service.CategoryService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listAllCategories() {
        return ResponseEntity.ok(categoryService.listAllCategories());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@RequestParam @NotBlank String name) {
        return ResponseEntity.ok(categoryService.createCategory(name));
    }

    @PostMapping("/{categoryId}/subcategories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubcategoryResponse> createSubcategory(
            @PathVariable Long categoryId,
            @RequestParam @NotBlank String name) {
        return ResponseEntity.ok(categoryService.createSubcategory(categoryId, name));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestParam @NotBlank String name) {
        return ResponseEntity.ok(categoryService.updateCategory(id, name));
    }

    @PutMapping("/subcategories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubcategoryResponse> updateSubcategory(
            @PathVariable Long id,
            @RequestParam @NotBlank String name) {
        return ResponseEntity.ok(categoryService.updateSubcategory(id, name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subcategories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSubcategory(@PathVariable Long id) {
        categoryService.deleteSubcategory(id);
        return ResponseEntity.noContent().build();
    }
}
