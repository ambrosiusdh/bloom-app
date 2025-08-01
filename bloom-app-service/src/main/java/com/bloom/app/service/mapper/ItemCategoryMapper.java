package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.request.itemcategory.CreateItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.UpdateItemCategoryRequest;
import com.bloom.app.domain.dto.response.itemcategory.ItemCategoryResponse;
import com.bloom.app.domain.model.ItemCategory;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ItemCategoryMapper {
    ItemCategoryResponse entityToResponse(ItemCategory itemCategory);
    void copyUpdateRequestToEntity(UpdateItemCategoryRequest request, @MappingTarget ItemCategory itemCategory);
    ItemCategory createRequestToEntity(CreateItemCategoryRequest request);
}
