package com.freestone.pettycash.mapper;

import com.freestone.pettycash.dto.CategoryResponse;
import com.freestone.pettycash.dto.SubcategoryResponse;
import com.freestone.pettycash.model.Category;
import com.freestone.pettycash.model.Subcategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);

    @Mapping(target = "categoryId", source = "category.id")
    SubcategoryResponse toResponse(Subcategory subcategory);
}
