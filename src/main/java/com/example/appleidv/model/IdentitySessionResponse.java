package com.example.appleidv.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdentitySessionResponse(
        String sessionId,
        String qrContent,
        @JsonProperty("payload") DigitalCredentialRequestPayload request
) {
}
