package com.example.appleidv.model;

public record IdentitySessionResponse(
        String sessionId,
        String qrContent,
        DigitalCredentialRequestPayload request
) {
}

