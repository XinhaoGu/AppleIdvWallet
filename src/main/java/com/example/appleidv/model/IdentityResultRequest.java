package com.example.appleidv.model;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record IdentityResultRequest(
        @NotNull Boolean hasValidId,
        Map<String, Object> walletResponse
) {
}

