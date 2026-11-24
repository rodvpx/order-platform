package com.backendlab.product_service.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductFilterRequest(
    @Size(max = 150) String name,
    UUID categoryId,
    @PositiveOrZero BigDecimal minPrice,
    @PositiveOrZero BigDecimal maxPrice) {

  @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
  public boolean isPriceRangeValid() {
    if (minPrice == null || maxPrice == null) {
      return true;
    }
    return minPrice.compareTo(maxPrice) <= 0;
  }
}
