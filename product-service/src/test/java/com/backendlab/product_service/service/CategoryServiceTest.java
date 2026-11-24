package com.backendlab.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backendlab.product_service.dto.request.CategoryRequest;
import com.backendlab.product_service.dto.response.CategoryResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.exception.BusinessRuleException;
import com.backendlab.product_service.exception.ConflictException;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.CategoryMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import com.backendlab.product_service.repository.ProductRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductRepository productRepository;
  @Mock private CategoryMapper categoryMapper;

  @InjectMocks private CategoryService categoryService;

  // ---------- findById ----------

  @Test
  void shouldReturnCategoryWhenIdExists() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = category(id, "Livros", "livros", null);
    CategoryResponse expected = response(category);
    when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
    when(categoryMapper.toResponse(category)).thenReturn(expected);

    // When
    CategoryResponse actual = categoryService.findById(id);

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(categoryRepository).findById(id);
    verify(categoryMapper).toResponse(category);
  }

  @Test
  void shouldThrowNotFoundWhenCategoryDoesNotExist() {
    // Given
    UUID id = UUID.randomUUID();
    when(categoryRepository.findById(id)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> categoryService.findById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found: " + id);
    verifyNoInteractions(categoryMapper);
  }

  // ---------- findAll ----------

  @Test
  void shouldReturnAllCategoriesMappedSortedByName() {
    // Given
    Category first = category(UUID.randomUUID(), "Jogos", "jogos", null);
    Category second = category(UUID.randomUUID(), "Livros", "livros", null);
    CategoryResponse firstResponse = response(first);
    CategoryResponse secondResponse = response(second);
    when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(first, second));
    when(categoryMapper.toResponse(first)).thenReturn(firstResponse);
    when(categoryMapper.toResponse(second)).thenReturn(secondResponse);

    // When
    List<CategoryResponse> actual = categoryService.findAll();

    // Then
    assertThat(actual).containsExactly(firstResponse, secondResponse);

    ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
    verify(categoryRepository).findAll(sortCaptor.capture());
    assertThat(sortCaptor.getValue().toList())
        .extracting(Sort.Order::getProperty)
        .containsExactly("name");
  }

  @Test
  void shouldReturnEmptyListWhenThereAreNoCategories() {
    // Given
    when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of());

    // When
    List<CategoryResponse> actual = categoryService.findAll();

    // Then
    assertThat(actual).isEmpty();
    verifyNoInteractions(categoryMapper);
  }

  // ---------- create ----------

  @Test
  void shouldCreateCategoryWithSlugGeneratedFromName() {
    // Given
    CategoryRequest request = new CategoryRequest("Eletrônicos & Cia", null);
    UUID id = UUID.randomUUID();
    Category entity = Category.builder().id(id).name(request.name()).build();
    CategoryResponse expected =
        new CategoryResponse(id, request.name(), "eletronicos-cia", null, CREATED_AT);
    when(categoryRepository.existsBySlug("eletronicos-cia")).thenReturn(false);
    when(categoryMapper.toEntity(request)).thenReturn(entity);
    when(categoryRepository.save(entity)).thenReturn(entity);
    when(categoryMapper.toResponse(entity)).thenReturn(expected);

    // When
    CategoryResponse actual = categoryService.create(request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(entity.getSlug()).isEqualTo("eletronicos-cia");
    assertThat(entity.getParent()).isNull();
    verify(categoryRepository).save(entity);
    verify(categoryMapper).toResponse(entity);
  }

  @Test
  void shouldCreateCategoryWithParentWhenParentIdIsProvided() {
    // Given
    Category parent = category(UUID.randomUUID(), "Livros", "livros", null);
    CategoryRequest request = new CategoryRequest("Romances", parent.getId());
    Category entity = Category.builder().id(UUID.randomUUID()).name(request.name()).build();
    CategoryResponse expected =
        new CategoryResponse(entity.getId(), "Romances", "romances", parent.getId(), CREATED_AT);
    when(categoryRepository.existsBySlug("romances")).thenReturn(false);
    when(categoryMapper.toEntity(request)).thenReturn(entity);
    when(categoryRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
    when(categoryRepository.save(entity)).thenReturn(entity);
    when(categoryMapper.toResponse(entity)).thenReturn(expected);

    // When
    CategoryResponse actual = categoryService.create(request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(entity.getParent()).isSameAs(parent);
    verify(categoryRepository).save(entity);
  }

  @Test
  void shouldThrowConflictWhenSlugAlreadyExistsOnCreate() {
    // Given
    CategoryRequest request = new CategoryRequest("Livros", null);
    when(categoryRepository.existsBySlug("livros")).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> categoryService.create(request))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Category slug already exists: livros");
    verify(categoryRepository, never()).save(any());
    verifyNoInteractions(categoryMapper);
  }

  @Test
  void shouldThrowNotFoundWhenParentDoesNotExistOnCreate() {
    // Given
    UUID parentId = UUID.randomUUID();
    CategoryRequest request = new CategoryRequest("Romances", parentId);
    when(categoryRepository.existsBySlug("romances")).thenReturn(false);
    when(categoryMapper.toEntity(request)).thenReturn(Category.builder().name("Romances").build());
    when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> categoryService.create(request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found: " + parentId);
    verify(categoryRepository, never()).save(any());
  }

  @Test
  void shouldThrowBusinessRuleWhenNameHasNoLettersOrNumbers() {
    // Given
    CategoryRequest request = new CategoryRequest("###", null);

    // When / Then
    assertThatThrownBy(() -> categoryService.create(request))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("Category name must contain letters or numbers");
    verifyNoInteractions(categoryRepository, categoryMapper);
  }

  // ---------- update ----------

  @Test
  void shouldUpdateCategoryAndClearParentWhenParentIdIsNull() {
    // Given
    Category oldParent = category(UUID.randomUUID(), "Livros", "livros", null);
    Category category = category(UUID.randomUUID(), "Livros Antigos", "livros-antigos", oldParent);
    CategoryRequest request = new CategoryRequest("Novos Livros", null);
    CategoryResponse expected =
        new CategoryResponse(category.getId(), "Novos Livros", "novos-livros", null, CREATED_AT);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("novos-livros", category.getId()))
        .thenReturn(false);
    when(categoryMapper.toResponse(category)).thenReturn(expected);

    // When
    CategoryResponse actual = categoryService.update(category.getId(), request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(category.getSlug()).isEqualTo("novos-livros");
    assertThat(category.getParent()).isNull();
    verify(categoryMapper).updateEntity(request, category);
    verify(categoryRepository, never()).save(any());
  }

  @Test
  void shouldSetNewParentWhenParentIdIsProvidedOnUpdate() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    Category newParent = category(UUID.randomUUID(), "Cultura", "cultura", null);
    CategoryRequest request = new CategoryRequest("Livros", newParent.getId());
    CategoryResponse expected = response(category);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("livros", category.getId())).thenReturn(false);
    when(categoryRepository.findById(newParent.getId())).thenReturn(Optional.of(newParent));
    when(categoryMapper.toResponse(category)).thenReturn(expected);

    // When
    CategoryResponse actual = categoryService.update(category.getId(), request);

    // Then
    assertThat(actual).isEqualTo(expected);
    assertThat(category.getParent()).isSameAs(newParent);
    verify(categoryRepository, never()).save(any());
  }

  @Test
  void shouldThrowConflictWhenSlugBelongsToAnotherCategoryOnUpdate() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    CategoryRequest request = new CategoryRequest("Jogos", null);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("jogos", category.getId())).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> categoryService.update(category.getId(), request))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Category slug already exists: jogos");
    verify(categoryMapper, never()).updateEntity(any(), any());
    verify(categoryRepository, never()).save(any());
  }

  @Test
  void shouldThrowNotFoundWhenCategoryDoesNotExistOnUpdate() {
    // Given
    UUID id = UUID.randomUUID();
    CategoryRequest request = new CategoryRequest("Livros", null);
    when(categoryRepository.findById(id)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> categoryService.update(id, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found: " + id);
    verifyNoInteractions(categoryMapper);
  }

  @Test
  void shouldThrowNotFoundWhenNewParentDoesNotExistOnUpdate() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    UUID parentId = UUID.randomUUID();
    CategoryRequest request = new CategoryRequest("Livros", parentId);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("livros", category.getId())).thenReturn(false);
    when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> categoryService.update(category.getId(), request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found: " + parentId);
    verify(categoryRepository, never()).save(any());
  }

  @Test
  void shouldThrowBusinessRuleWhenCategoryIsItsOwnParent() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    CategoryRequest request = new CategoryRequest("Livros", category.getId());
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("livros", category.getId())).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> categoryService.update(category.getId(), request))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("Category cannot be its own ancestor");
    assertThat(category.getParent()).isNull();
  }

  @Test
  void shouldThrowBusinessRuleWhenNewParentIsDescendant() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    Category child = category(UUID.randomUUID(), "Romances", "romances", category);
    Category grandChild = category(UUID.randomUUID(), "Policial", "policial", child);
    CategoryRequest request = new CategoryRequest("Livros", grandChild.getId());
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("livros", category.getId())).thenReturn(false);
    when(categoryRepository.findById(grandChild.getId())).thenReturn(Optional.of(grandChild));

    // When / Then
    assertThatThrownBy(() -> categoryService.update(category.getId(), request))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("Category cannot be its own ancestor");
    assertThat(category.getParent()).isNull();
  }

  @Test
  void shouldNotFailWhenExistingParentChainIsAlreadyCyclic() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    Category first = Category.builder().id(UUID.randomUUID()).name("A").slug("a").build();
    Category second = Category.builder().id(UUID.randomUUID()).name("B").slug("b").build();
    first.setParent(second);
    second.setParent(first);
    CategoryRequest request = new CategoryRequest("Livros", first.getId());
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsBySlugAndIdNot("livros", category.getId())).thenReturn(false);
    when(categoryRepository.findById(first.getId())).thenReturn(Optional.of(first));
    when(categoryMapper.toResponse(category)).thenReturn(response(category));

    // When / Then
    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> assertThat(categoryService.update(category.getId(), request)).isNotNull());
    assertThat(category.getParent()).isSameAs(first);
  }

  // ---------- delete ----------

  @Test
  void shouldDeleteCategoryWhenItHasNoChildrenOrProducts() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsByParentId(category.getId())).thenReturn(false);
    when(productRepository.existsByCategoryId(category.getId())).thenReturn(false);

    // When
    categoryService.delete(category.getId());

    // Then
    verify(categoryRepository).delete(category);
  }

  @Test
  void shouldThrowNotFoundWhenCategoryDoesNotExistOnDelete() {
    // Given
    UUID id = UUID.randomUUID();
    when(categoryRepository.findById(id)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> categoryService.delete(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found: " + id);
    verify(categoryRepository, never()).delete(any());
  }

  @Test
  void shouldThrowConflictWhenCategoryHasSubcategories() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsByParentId(category.getId())).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> categoryService.delete(category.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Category has subcategories: " + category.getId());
    verifyNoInteractions(productRepository);
    verify(categoryRepository, never()).delete(any());
  }

  @Test
  void shouldThrowConflictWhenCategoryHasProducts() {
    // Given
    Category category = category(UUID.randomUUID(), "Livros", "livros", null);
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(categoryRepository.existsByParentId(category.getId())).thenReturn(false);
    when(productRepository.existsByCategoryId(category.getId())).thenReturn(true);

    // When / Then
    assertThatThrownBy(() -> categoryService.delete(category.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Category has products: " + category.getId());
    verify(categoryRepository, never()).delete(any());
  }

  // ---------- helpers ----------

  private static Category category(UUID id, String name, String slug, Category parent) {
    return Category.builder().id(id).name(name).slug(slug).parent(parent).build();
  }

  private static CategoryResponse response(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getSlug(),
        category.getParent() == null ? null : category.getParent().getId(),
        CREATED_AT);
  }
}
