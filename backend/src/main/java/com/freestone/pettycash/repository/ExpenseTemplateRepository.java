package com.freestone.pettycash.repository;

import com.freestone.pettycash.model.ExpenseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpenseTemplateRepository extends JpaRepository<ExpenseTemplate, Long> {
    Optional<ExpenseTemplate> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
