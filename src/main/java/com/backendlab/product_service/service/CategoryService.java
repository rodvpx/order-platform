package com.backendlab.product_service.service;

import com.backendlab.product_service.dto.request.CategoryRequest;
import com.backendlab.product_service.dto.response.CategoryResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.exception.BusinessRuleException;
import com.backendlab.product_service.exception.ConflictException;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.CategoryMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import com.backendlab.product_service.repository.ProductRepository;
import com.backendlab.product_service.util.SlugGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  private final ProductRepository productRepository;

  @Transactional
  public CategoryResponse create(CategoryRequest request) {
    String slug = generateValidSlug(request.name());

    if (categoryRepository.existsBySlug(slug)) {
      throw new ConflictException("Category slug already exists: " + slug);
    }

    Category category = categoryMapper.toEntity(request);
    category.setSlug(slug);

    if (request.parentId() != null) {
      category.setParent(getCategoryOrThrow(request.parentId()));
    }

    Category saved = categoryRepository.save(category);

    return categoryMapper.toResponse(saved);
  }

  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    Category category = getCategoryOrThrow(id);
    String slug = generateValidSlug(request.name());

    if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
      throw new ConflictException("Category slug already exists: " + slug);
    }

    categoryMapper.updateEntity(request, category);
    category.setSlug(slug);

    if (request.parentId() == null) {
      category.setParent(null);
    } else {
      Category newParent = getCategoryOrThrow(request.parentId());
      validateNoCycle(category, newParent);
      category.setParent(newParent);
    }

    return categoryMapper.toResponse(category);
  }

  private void validateNoCycle(Category category, Category newParent) {
    Set<UUID> visited = new HashSet<>();
    Category current = newParent;

    while (current != null) {
      if (current.getId().equals(category.getId())) {
        throw new BusinessRuleException("Category cannot be its own ancestor");
      }

      if (!visited.add(current.getId())) {
        return;
      }

      current = current.getParent();
    }
  }

  @Transactional
  public void delete(UUID id) {
    Category category = getCategoryOrThrow(id);

    if (categoryRepository.existsByParentId(id)) {
      throw new ConflictException("Category has subcategories: " + id);
    }

    if (productRepository.existsByCategoryId(id)) {
      throw new ConflictException("Category has products: " + id);
    }

    categoryRepository.delete(category);
  }

  private String generateValidSlug(String name) {
    String slug = SlugGenerator.generate(name);

    if (slug.isEmpty()) {
      throw new BusinessRuleException("Category name must contain letters or numbers");
    }

    return slug;
  }

  @Transactional(readOnly = true)
  public CategoryResponse findById(UUID id) {
    return categoryMapper.toResponse(getCategoryOrThrow(id));
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> findAll() {
    return categoryRepository.findAll(Sort.by("name")).stream()
        .map(categoryMapper::toResponse)
        .toList();
  }

  private Category getCategoryOrThrow(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
  }
}
