package com.backendlab.product_service.service;

import com.backendlab.product_service.dto.request.ProductFilterRequest;
import com.backendlab.product_service.dto.request.ProductRequest;
import com.backendlab.product_service.dto.request.ProductUpdateRequest;
import com.backendlab.product_service.dto.response.ProductResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.entity.Product;
import com.backendlab.product_service.exception.ConflictException;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.ProductMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import com.backendlab.product_service.repository.ProductRepository;
import com.backendlab.product_service.repository.specification.ProductSpecifications;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final ProductMapper productMapper;

  @Transactional
  public ProductResponse create(ProductRequest request, UUID createdBy) {
    Category category = getCategoryOrThrow(request.categoryId());

    if (productRepository.existsBySku(request.sku())) {
      throw new ConflictException("Product SKU already exists: " + request.sku());
    }

    Product product = productMapper.toEntity(request);
    product.setCategory(category);
    product.setCreatedBy(createdBy);
    product.setActive(true);

    return productMapper.toResponse(productRepository.save(product));
  }

  @Transactional(readOnly = true)
  public ProductResponse findById(UUID id, boolean isAdmin) {
    return productMapper.toResponse(getProductOrThrow(id, isAdmin));
  }

  @Transactional(readOnly = true)
  public Page<ProductResponse> findAll(ProductFilterRequest filter, Pageable pageable) {
    Specification<Product> spec =
        Specification.allOf(
            ProductSpecifications.isActive(),
            ProductSpecifications.nameContains(filter.name()),
            ProductSpecifications.inCategory(filter.categoryId()),
            ProductSpecifications.priceAtLeast(filter.minPrice()),
            ProductSpecifications.priceAtMost(filter.maxPrice()));

    return productRepository.findAll(spec, pageable).map(productMapper::toResponse);
  }

  @Transactional
  public ProductResponse update(UUID id, ProductUpdateRequest request) {
    Product product = getProductOrThrow(id, true);

    if (!Objects.equals(product.getVersion(), request.version())) {
      throw new ConflictException(
          "Product version mismatch: expected "
              + product.getVersion()
              + " but received "
              + request.version());
    }

    productMapper.updateEntity(request, product);

    if (!product.getCategory().getId().equals(request.categoryId())) {
      product.setCategory(getCategoryOrThrow(request.categoryId()));
    }

    return productMapper.toResponse(productRepository.saveAndFlush(product));
  }

  @Transactional
  public void deactivate(UUID id) {
    Product product = getProductOrThrow(id, false);
    product.setActive(false);
  }

  private Product getProductOrThrow(UUID id, boolean includeInactive) {
    Optional<Product> product =
        includeInactive
            ? productRepository.findById(id)
            : productRepository.findByIdAndActiveTrue(id);

    return product.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
  }

  private Category getCategoryOrThrow(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
  }
}
