package com.freestone.pettycash.service;

import com.freestone.pettycash.dto.CategoryResponse;
import com.freestone.pettycash.dto.SubcategoryResponse;
import com.freestone.pettycash.exception.ResourceNotFoundException;
import com.freestone.pettycash.mapper.CategoryMapper;
import com.freestone.pettycash.model.Category;
import com.freestone.pettycash.model.Subcategory;
import com.freestone.pettycash.repository.CategoryRepository;
import com.freestone.pettycash.repository.SubcategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final CategoryMapper mapper;

    public List<CategoryResponse> listAllCategories() {
        return categoryRepository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category with name '%s' already exists".formatted(name));
        }
        Category category = new Category(name);
        return mapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public SubcategoryResponse createSubcategory(Long categoryId, String name) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(categoryId, name)) {
            throw new IllegalArgumentException("Subcategory with name '%s' already exists in this category".formatted(name));
        }

        Subcategory subcategory = new Subcategory(name, category);
        return mapper.toResponse(subcategoryRepository.save(subcategory));
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
        categoryRepository.deleteById(categoryId);
        log.info("Deleted category id: {}", categoryId);
    }

    @Transactional
    public void deleteSubcategory(Long subcategoryId) {
        if (!subcategoryRepository.existsById(subcategoryId)) {
            throw new ResourceNotFoundException("Subcategory", "id", subcategoryId);
        }
        subcategoryRepository.deleteById(subcategoryId);
        log.info("Deleted subcategory id: {}", subcategoryId);
    }
}
