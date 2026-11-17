package com.backendlab.product_service.service;

import com.backendlab.product_service.dto.response.CategoryResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.CategoryMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public CategoryResponse findById(UUID id) {
        return categoryMapper.toResponse(getCategoryOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(categoryMapper::toResponse).toList();
    }

    private Category getCategoryOrThrow(UUID id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}
