package com.freestone.pettycash.mapper;

import com.freestone.pettycash.dto.TransactionResponse;
import com.freestone.pettycash.model.PettyCashTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "subcategoryId", source = "subcategory.id")
    @Mapping(target = "subcategoryName", source = "subcategory.name")
    TransactionResponse toResponse(PettyCashTransaction transaction);
}
