package com.crowdfunding.backend.exception;

import java.util.List;

public record ErrorResponse(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    List<ErrorDetail> details
) {
    // Record anidado para los detalles de validación
    public record ErrorDetail(String field, String issue) {}
}
