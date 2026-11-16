package com.backendlab.product_service.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @Test
  void shouldReturn400WithFieldErrorsWhenRequestBodyIsInvalid() throws Exception {
    // Given
    String invalidBody = """
                {"name": "", "price": -1}
                """;

    // When
    ResultActions result =
        mockMvc.perform(
            post("/test/validation").contentType(MediaType.APPLICATION_JSON).content(invalidBody));

    // Then
    result
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Validation error"))
        .andExpect(jsonPath("$.detail").value("Validation failed"))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists());
  }

  @Test
  void shouldReturn400WhenRequestBodyIsMalformedJson() throws Exception {
    // Given
    String malformedBody = "{ invalid json";

    // When
    ResultActions result =
        mockMvc.perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedBody));

    // Then
    result
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.detail").value("Malformed request body"));
  }

  @Test
  void shouldReturn400WhenPathVariableHasInvalidType() throws Exception {
    // Given
    String invalidId = "not-a-uuid";

    // When
    ResultActions result = mockMvc.perform(get("/test/uuid/" + invalidId));

    // Then
    result
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.detail").value("Invalid value for parameter: id"));
  }

  @Test
  void shouldReturn409WhenOptimisticLockingFailureOccurs() throws Exception {
    // Given
    // stub endpoint throws ObjectOptimisticLockingFailureException

    // When
    ResultActions result = mockMvc.perform(get("/test/optimistic-lock"));

    // Then
    result
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(
            jsonPath("$.detail")
                .value("The resource was modified by another request. Reload it and try again"));
  }

  @Test
  void shouldReturn500WithGenericDetailWhenUnexpectedExceptionIsThrown() throws Exception {
    // Given
    // stub endpoint throws IllegalStateException with an internal message

    // When
    ResultActions result = mockMvc.perform(get("/test/unexpected"));

    // Then
    result
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }

  @Test
  void shouldKeep405WhenHttpMethodIsNotSupported() throws Exception {
    // Given
    // /test/conflict only accepts GET

    // When
    ResultActions result = mockMvc.perform(post("/test/conflict"));

    // Then
    result
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
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

    @PostMapping("/validation")
    public void validate(@Valid @RequestBody StubRequest request) {}

    public record StubRequest(@NotBlank String name, @NotNull @Positive BigDecimal price) {}

    @GetMapping("/uuid/{id}")
    public void byId(@PathVariable("id") UUID id) {}

    @GetMapping("/optimistic-lock")
    public void throwOptimisticLock() {
      throw new ObjectOptimisticLockingFailureException(Object.class, 42L);
    }

    @GetMapping("/unexpected")
    public void throwUnexpected() {
      throw new IllegalStateException("internal secret detail");
    }
  }
}
