package com.backendlab.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backendlab.product_service.dto.response.CategoryResponse;
import com.backendlab.product_service.entity.Category;
import com.backendlab.product_service.exception.ResourceNotFoundException;
import com.backendlab.product_service.mapper.CategoryMapper;
import com.backendlab.product_service.repository.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private CategoryMapper categoryMapper;

  @InjectMocks private CategoryService categoryService;

  @Test
  void shouldReturnCategoryWhenIdExists() {
    // Given
    UUID id = UUID.randomUUID();
    Category category = Category.builder().id(id).name("Livros").slug("livros").build();
    CategoryResponse expected =
        new CategoryResponse(id, "Livros", "livros", null, LocalDateTime.of(2026, 1, 1, 10, 0));
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
        .hasMessageContaining(id.toString());
    verifyNoInteractions(categoryMapper);
  }

  @Test
  void shouldReturnAllCategoriesMapped() {
    // Given
    Category first = Category.builder().id(UUID.randomUUID()).name("Livros").slug("livros").build();
    Category second = Category.builder().id(UUID.randomUUID()).name("Jogos").slug("jogos").build();
    CategoryResponse firstResponse =
        new CategoryResponse(first.getId(), "Livros", "livros", null, LocalDateTime.now());
    CategoryResponse secondResponse =
        new CategoryResponse(second.getId(), "Jogos", "jogos", null, LocalDateTime.now());
    when(categoryRepository.findAll()).thenReturn(List.of(first, second));
    when(categoryMapper.toResponse(first)).thenReturn(firstResponse);
    when(categoryMapper.toResponse(second)).thenReturn(secondResponse);

    // When
    List<CategoryResponse> actual = categoryService.findAll();

    // Then
    assertThat(actual).containsExactly(firstResponse, secondResponse);
    verify(categoryRepository).findAll();
  }

  @Test
  void shouldReturnEmptyListWhenThereAreNoCategories() {
    // Given
    when(categoryRepository.findAll()).thenReturn(List.of());

    // When
    List<CategoryResponse> actual = categoryService.findAll();

    // Then
    assertThat(actual).isEmpty();
    verifyNoInteractions(categoryMapper);
  }
}
