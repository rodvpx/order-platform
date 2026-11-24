package com.backendlab.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.backendlab.product_service.dto.request.ProductFilterRequest;
import com.backendlab.product_service.dto.request.ProductRequest;
import com.backendlab.product_service.dto.request.ProductUpdateRequest;
import com.backendlab.product_service.dto.response.CategoryResponse;
import com.backendlab.product_service.dto.response.ProductResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.entity.Product;
import com.backendlab.product_service.exception.ConflictException;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.ProductMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import com.backendlab.product_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
  private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 1, 2, 11, 0);
  private static final UUID CREATED_BY = UUID.fromString("66bc52f1-d1ca-4898-b6f0-05129359f5cd");
  private static final String SKU = "SKU-001";
  private static final String NAME = "Moto G56";
  private static final String DESCRIPTION = "Smartphone";
  private static final BigDecimal PRICE = new BigDecimal("999.90");

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductMapper productMapper;

  @InjectMocks private ProductService productService;

  // ---------- create ----------

  @Test
  @DisplayName("RN07: createdBy é preenchido pelo serviço e o produto nasce ativo")
  void shouldCreateActiveProductWithCreatedByAndCategoryWhenRequestIsValid() {
    // Given
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    ProductRequest request = createRequest(category.getId());
    Product entity =
        Product.builder()
            .sku(request.sku())
            .name(request.name())
            .description(request.description())
            .price(request.price())
            .build();
    Product saved = product(UUID.randomUUID(), category, true, 0);
    ProductResponse expected = response(saved);
    given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
    given(productRepository.existsBySku(request.sku())).willReturn(false);
    given(productMapper.toEntity(request)).willReturn(entity);
    given(productRepository.save(entity)).willReturn(saved);
    given(productMapper.toResponse(saved)).willReturn(expected);

    // When
    ProductResponse actual = productService.create(request, CREATED_BY);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(entity.getCreatedBy()).isEqualTo(CREATED_BY);
    assertThat(entity.isActive()).isTrue();
    assertThat(entity.getCategory()).isSameAs(category);
    verify(productRepository).save(entity);
    verify(productMapper).toResponse(saved);
  }

  @Test
  @DisplayName("RN03: todo produto pertence a uma categoria existente")
  void shouldThrowNotFoundWhenCategoryDoesNotExistOnCreate() {
    // Given
    UUID categoryId = UUID.randomUUID();
    ProductRequest request = createRequest(categoryId);
    given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.create(request, CREATED_BY))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Category not found: " + categoryId);
    verifyNoInteractions(productRepository, productMapper);
  }

  @Test
  @DisplayName("RN01: o sku é único no sistema")
  void shouldThrowConflictWhenSkuAlreadyExistsOnCreate() {
    // Given
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    ProductRequest request = createRequest(category.getId());
    given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
    given(productRepository.existsBySku(request.sku())).willReturn(true);

    // When / Then
    assertThatThrownBy(() -> productService.create(request, CREATED_BY))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Product SKU already exists: " + request.sku());
    verify(productRepository, never()).save(any());
    verifyNoInteractions(productMapper);
  }

  // ---------- findById ----------

  @Test
  @DisplayName("RN06: produto ativo é retornado para quem não é admin")
  void shouldReturnActiveProductWhenUserIsNotAdmin() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    ProductResponse expected = response(product);
    given(productRepository.findByIdAndActiveTrue(id)).willReturn(Optional.of(product));
    given(productMapper.toResponse(product)).willReturn(expected);

    // When
    ProductResponse actual = productService.findById(id, false);

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(productRepository).findByIdAndActiveTrue(id);
    verify(productRepository, never()).findById(any());
  }

  @Test
  @DisplayName("RN06: produto inativo retorna 404 para quem não é admin")
  void shouldThrowNotFoundWhenProductIsInactiveAndUserIsNotAdmin() {
    // Given
    UUID id = UUID.randomUUID();
    given(productRepository.findByIdAndActiveTrue(id)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.findById(id, false))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product not found: " + id);
    verify(productRepository).findByIdAndActiveTrue(id);
    verify(productRepository, never()).findById(any());
    verifyNoInteractions(productMapper);
  }

  @Test
  @DisplayName("RN06: admin pode consultar produto inativo")
  void shouldReturnInactiveProductWhenUserIsAdmin() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, false, 0);
    ProductResponse expected = response(product);
    given(productRepository.findById(id)).willReturn(Optional.of(product));
    given(productMapper.toResponse(product)).willReturn(expected);

    // When
    ProductResponse actual = productService.findById(id, true);

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(productRepository).findById(id);
    verify(productRepository, never()).findByIdAndActiveTrue(any());
  }

  @Test
  @DisplayName("RN06: produto inexistente retorna 404")
  void shouldThrowNotFoundWhenProductDoesNotExistOnFindById() {
    // Given
    UUID id = UUID.randomUUID();
    given(productRepository.findByIdAndActiveTrue(id)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.findById(id, false))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product not found: " + id);
    verifyNoInteractions(productMapper);
  }

  // ---------- findAll ----------

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("RN06: a listagem pública filtra produtos ativos e mapeia a página")
  void shouldDelegateSpecificationAndPageableAndMapPageContentWhenFindingAll() {
    // Given
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product first = product(UUID.randomUUID(), category, true, 0);
    Product second = product(UUID.randomUUID(), category, true, 0);
    ProductResponse firstResponse = response(first);
    ProductResponse secondResponse = response(second);
    Pageable pageable = PageRequest.of(0, 10);
    ProductFilterRequest filter = new ProductFilterRequest(null, null, null, null);
    Page<Product> page = new PageImpl<>(List.of(first, second), pageable, 2);
    given(productRepository.findAll(any(Specification.class), eq(pageable))).willReturn(page);
    given(productMapper.toResponse(first)).willReturn(firstResponse);
    given(productMapper.toResponse(second)).willReturn(secondResponse);

    // When
    Page<ProductResponse> actual = productService.findAll(filter, pageable);

    // Then
    assertThat(actual.getContent()).containsExactly(firstResponse, secondResponse);
    assertThat(actual.getTotalElements()).isEqualTo(2);

    ArgumentCaptor<Specification<Product>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(productRepository).findAll(specCaptor.capture(), eq(pageable));
    assertThat(specCaptor.getValue()).isNotNull();
    verify(productMapper).toResponse(first);
    verify(productMapper).toResponse(second);
  }

  // ---------- update ----------

  @Test
  @DisplayName("RN08: atualiza o produto quando a version informada é a atual")
  void shouldUpdateProductWhenVersionMatches() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    ProductUpdateRequest request = updateRequest(category.getId(), 0);
    ProductResponse expected = response(product);
    given(productRepository.findById(id)).willReturn(Optional.of(product));
    given(productRepository.saveAndFlush(product)).willReturn(product);
    given(productMapper.toResponse(product)).willReturn(expected);

    // When
    ProductResponse actual = productService.update(id, request);

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(productMapper).updateEntity(request, product);
    verify(productRepository).saveAndFlush(product);
  }

  @Test
  @DisplayName("RN08: version desatualizada falha com 409 e não altera o produto")
  void shouldThrowConflictWhenVersionDoesNotMatch() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    ProductUpdateRequest request = updateRequest(category.getId(), 1);
    given(productRepository.findById(id)).willReturn(Optional.of(product));

    // When / Then
    assertThatThrownBy(() -> productService.update(id, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Product version mismatch: expected 0 but received 1");
    verifyNoInteractions(productMapper);
    verify(productRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RN08: atualizar produto inexistente retorna 404")
  void shouldThrowNotFoundWhenProductDoesNotExistOnUpdate() {
    // Given
    UUID id = UUID.randomUUID();
    ProductUpdateRequest request = updateRequest(UUID.randomUUID(), 0);
    given(productRepository.findById(id)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.update(id, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product not found: " + id);
    verifyNoInteractions(productMapper, categoryRepository);
    verify(productRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RN03: nova categoria é buscada e atribuída ao produto")
  void shouldAssignNewCategoryWhenCategoryChanges() {
    // Given
    UUID id = UUID.randomUUID();
    Category oldCategory = category(UUID.randomUUID(), "Eletrônicos");
    Category newCategory = category(UUID.randomUUID(), "Informática");
    Product product = product(id, oldCategory, true, 0);
    ProductUpdateRequest request = updateRequest(newCategory.getId(), 0);
    ProductResponse expected = response(product, newCategory);
    given(productRepository.findById(id)).willReturn(Optional.of(product));
    given(categoryRepository.findById(newCategory.getId())).willReturn(Optional.of(newCategory));
    given(productRepository.saveAndFlush(product)).willReturn(product);
    given(productMapper.toResponse(product)).willReturn(expected);

    // When
    ProductResponse actual = productService.update(id, request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(product.getCategory()).isSameAs(newCategory);
    verify(categoryRepository).findById(newCategory.getId());
    verify(productRepository).saveAndFlush(product);
  }

  @Test
  @DisplayName("RN03: mantém a categoria atual sem consultar o repositório de categorias")
  void shouldNotQueryCategoryRepositoryWhenCategoryIsUnchanged() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    ProductUpdateRequest request = updateRequest(category.getId(), 0);
    ProductResponse expected = response(product);
    given(productRepository.findById(id)).willReturn(Optional.of(product));
    given(productRepository.saveAndFlush(product)).willReturn(product);
    given(productMapper.toResponse(product)).willReturn(expected);

    // When
    ProductResponse actual = productService.update(id, request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(product.getCategory()).isSameAs(category);
    verify(productMapper).updateEntity(request, product);
    verifyNoInteractions(categoryRepository);
  }

  @Test
  @DisplayName("RN03: nova categoria inexistente retorna 404 e não salva o produto")
  void shouldThrowNotFoundWhenNewCategoryDoesNotExistOnUpdate() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    UUID newCategoryId = UUID.randomUUID();
    ProductUpdateRequest request = updateRequest(newCategoryId, 0);
    given(productRepository.findById(id)).willReturn(Optional.of(product));
    given(categoryRepository.findById(newCategoryId)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.update(id, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Category not found: " + newCategoryId);
    verify(productRepository, never()).saveAndFlush(any());
  }

  // ---------- deactivate ----------

  @Test
  @DisplayName("RN05: a exclusão é lógica e marca o produto como inativo")
  void shouldMarkProductAsInactiveWhenDeactivating() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(UUID.randomUUID(), "Eletrônicos");
    Product product = product(id, category, true, 0);
    given(productRepository.findByIdAndActiveTrue(id)).willReturn(Optional.of(product));

    // When
    productService.deactivate(id);

    // Then
    assertThat(product.isActive()).isFalse();
    verify(productRepository).findByIdAndActiveTrue(id);
    verify(productRepository, never()).save(any());
    verify(productRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RN05: produto inexistente ou já inativo retorna 404 ao desativar")
  void shouldThrowNotFoundWhenProductIsMissingOrAlreadyInactiveOnDeactivate() {
    // Given
    UUID id = UUID.randomUUID();
    given(productRepository.findByIdAndActiveTrue(id)).willReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> productService.deactivate(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product not found: " + id);
    verify(productRepository, never()).save(any());
    verify(productRepository, never()).saveAndFlush(any());
  }

  // ---------- helpers ----------

  private static Category category(UUID id, String name) {
    return Category.builder().id(id).name(name).slug(name.toLowerCase()).build();
  }

  private static Product product(UUID id, Category category, boolean active, int version) {
    return Product.builder()
        .id(id)
        .sku(SKU)
        .name(NAME)
        .description(DESCRIPTION)
        .price(PRICE)
        .category(category)
        .active(active)
        .createdBy(CREATED_BY)
        .version(version)
        .createdAt(CREATED_AT)
        .updatedAt(UPDATED_AT)
        .build();
  }

  private static ProductRequest createRequest(UUID categoryId) {
    return new ProductRequest(SKU, NAME, DESCRIPTION, PRICE, categoryId);
  }

  private static ProductUpdateRequest updateRequest(UUID categoryId, Integer version) {
    return new ProductUpdateRequest(NAME, DESCRIPTION, PRICE, categoryId, version);
  }

  private static ProductResponse response(Product product) {
    return response(product, product.getCategory());
  }

  private static ProductResponse response(Product product, Category category) {
    return new ProductResponse(
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getDescription(),
        product.getPrice(),
        categoryResponse(category),
        product.isActive(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }

  private static CategoryResponse categoryResponse(Category category) {
    if (category == null) {
      return null;
    }

    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getSlug(),
        category.getParent() == null ? null : category.getParent().getId(),
        category.getCreatedAt());
  }
}
