package com.freestone.pettycash.dto;

import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        List<SubcategoryResponse> subcategories
) {}
