package com.example.appleidv.model;

import java.util.Map;

public record DigitalCredentialRequestPayload(
        String protocol,
        Map<String, Object> request
) {
}
