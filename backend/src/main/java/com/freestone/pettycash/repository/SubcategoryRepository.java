package com.freestone.pettycash.repository;

import com.freestone.pettycash.model.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findByCategoryId(Long categoryId);
    Optional<Subcategory> findByCategoryIdAndNameIgnoreCase(Long categoryId, String name);
    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);
}
