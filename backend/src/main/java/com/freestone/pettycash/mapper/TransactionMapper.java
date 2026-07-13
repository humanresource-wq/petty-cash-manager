package com.freestone.pettycash.mapper;

import com.freestone.pettycash.config.AppProperties;
import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.model.PettyCashTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public abstract class TransactionMapper {

    @Autowired
    protected AppProperties appProperties;

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "subcategoryId", source = "subcategory.id")
    @Mapping(target = "subcategoryName", source = "subcategory.name")
    @Mapping(target = "editable", expression = "java(isEditable(transaction))")
    public abstract TransactionResponse toResponse(PettyCashTransaction transaction);

    protected boolean isEditable(PettyCashTransaction transaction) {
        if (transaction == null || transaction.getCreatedAt() == null) {
            return true;
        }
        java.time.Instant limit = transaction.getCreatedAt()
                .atZone(java.time.ZoneOffset.UTC)
                .plusMonths(appProperties.getTransaction().getEditLimit().getMonths())
                .plusDays(appProperties.getTransaction().getEditLimit().getDays())
                .toInstant();
        return java.time.Instant.now().isBefore(limit);
    }
}
