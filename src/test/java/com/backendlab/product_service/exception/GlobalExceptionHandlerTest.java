package com.backendlab.product_service.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private static final String NOT_FOUND_MESSAGE = "Product not found: 42";
  private static final String CONFLICT_MESSAGE = "Product SKU already exists";
  private static final String BUSINESS_RULE_MESSAGE = "Product SKU cannot be changed";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void shouldReturn404WhenResourceNotFoundExceptionIsThrown() throws Exception {
    // Given
    String expectedMessage = NOT_FOUND_MESSAGE;

    // When
    ResultActions result = mockMvc.perform(get("/test/resource-not-found"));

    // Then
    result
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.detail").value(expectedMessage));
  }

  @Test
  void shouldReturn409WhenConflictExceptionIsThrown() throws Exception {
    // Given
    String expectedMessage = CONFLICT_MESSAGE;

    // When
    ResultActions result = mockMvc.perform(get("/test/conflict"));

    // Then
    result
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value(expectedMessage));
  }

  @Test
  void shouldReturn422WhenBusinessRuleExceptionIsThrown() throws Exception {
    // Given
    String expectedMessage = BUSINESS_RULE_MESSAGE;

    // When
    ResultActions result = mockMvc.perform(get("/test/business-rule"));

    // Then
    result
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Business rule violated"))
        .andExpect(jsonPath("$.detail").value(expectedMessage));
  }

  @RestController
  @RequestMapping("/test")
  public static class StubController {

    @GetMapping("/resource-not-found")
    public void throwResourceNotFound() {
      throw new ResourceNotFoundException(NOT_FOUND_MESSAGE);
    }

    @GetMapping("/conflict")
    public void throwConflict() {
      throw new ConflictException(CONFLICT_MESSAGE);
    }

    @GetMapping("/business-rule")
    public void throwBusinessRule() {
      throw new BusinessRuleException(BUSINESS_RULE_MESSAGE);
    }
  }
}
