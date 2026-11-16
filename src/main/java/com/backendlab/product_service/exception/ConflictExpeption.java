package com.backendlab.product_service.exception;

public class ConflictExpeption extends RuntimeException {

    public ConflictExpeption(String message) {
        super(message);
    }

}
